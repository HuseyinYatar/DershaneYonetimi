import uuid

API = "/api/v1"


def exam_payload(catalog, branch_id: uuid.UUID) -> dict:
    return {
        "examTypeId": str(catalog.exam_type_id),
        "name": "Deneme",
        "examDate": "2026-10-10",
        "branchId": str(branch_id),
        "scoringSource": "INSTITUTIONAL",
    }


def test_requests_without_token_are_unauthorized(client) -> None:
    response = client.get(f"{API}/exam-types")

    assert (response.status_code, response.json()["code"]) == (401, "TOKEN_MISSING")


def test_teachers_cannot_create_exams(client, actors, catalog) -> None:
    response = client.post(
        f"{API}/mock-exams", headers=actors.teacher, json=exam_payload(catalog, actors.branch_id)
    )

    assert (response.status_code, response.json()["code"]) == (403, "ROLE_NOT_ALLOWED")


def test_admins_act_only_on_their_branch(client, actors, catalog) -> None:
    created = client.post(
        f"{API}/mock-exams", headers=actors.admin, json=exam_payload(catalog, actors.branch_id)
    )
    exam_id = created.json()["id"]

    response = client.get(f"{API}/mock-exams/{exam_id}", headers=actors.other_branch_admin)
    foreign = client.post(f"{API}/mock-exams", headers=actors.admin, json=exam_payload(catalog, uuid.uuid4()))

    assert (response.status_code, response.json()["code"]) == (403, "BRANCH_NOT_ALLOWED")
    assert (foreign.status_code, foreign.json()["code"]) == (403, "BRANCH_NOT_ALLOWED")


def test_super_admin_acts_on_every_branch(client, actors, catalog) -> None:
    created = client.post(
        f"{API}/mock-exams", headers=actors.super_admin, json=exam_payload(catalog, actors.branch_id)
    )

    assert created.status_code == 201, created.text
    assert (
        client.get(f"{API}/mock-exams/{created.json()['id']}", headers=actors.super_admin).status_code == 200
    )


def test_swagger_offers_keycloak_login_and_pasted_tokens(client) -> None:
    schema = client.get("/openapi.json").json()

    flow = schema["components"]["securitySchemes"]["keycloak"]["flows"]["authorizationCode"]
    assert flow["authorizationUrl"].endswith("/protocol/openid-connect/auth")
    assert schema["paths"][f"{API}/exam-types"]["get"]["security"] == [
        {"HTTPBearer": []},
        {"keycloak": ["openid"]},
    ]
    assert "security" not in schema["paths"]["/health/live"]["get"]


def test_weak_topic_setting_is_admin_only(client, actors) -> None:
    body = {"windowSize": 3, "successThreshold": "0.5"}

    assert (
        client.put(
            f"{API}/branches/{actors.branch_id}/weak-topic-setting", headers=actors.teacher, json=body
        ).status_code
        == 403
    )
    assert (
        client.put(
            f"{API}/branches/{actors.branch_id}/weak-topic-setting", headers=actors.admin, json=body
        ).status_code
        == 200
    )


def test_matching_rejects_students_of_another_branch(client, actors, catalog, processor) -> None:
    outsider = uuid.uuid4()
    exam_id = client.post(
        f"{API}/mock-exams", headers=actors.admin, json=exam_payload(catalog, actors.branch_id)
    ).json()["id"]
    client.put(
        f"{API}/mock-exams/{exam_id}/answer-key", headers=actors.admin, json={"items": catalog.answer_key()}
    )
    client.post(
        f"{API}/mock-exams/{exam_id}/import-jobs",
        headers=actors.admin,
        data={"opticalFormatId": str(catalog.optical_format_id)},
        files={"file": ("f.txt", b"ST0001AABCDEA\n", "text/plain")},
    )
    processor.process_next()
    job = client.get(f"{API}/mock-exams/{exam_id}", headers=actors.admin)
    assert job.json()["status"] == "PROCESSED"

    from sqlalchemy import select

    from app.db import get_session_factory
    from app.models import StudentReplica, UnmatchedRow

    with get_session_factory()() as session:
        session.add(
            StudentReplica(
                student_id=outsider,
                student_no="ST0001",
                branch_id=uuid.uuid4(),
                class_id=uuid.uuid4(),
                full_name="X",
            )
        )
        session.commit()
        row_id = session.scalar(select(UnmatchedRow.id))

    response = client.post(
        f"{API}/unmatched-rows/{row_id}/match", headers=actors.admin, json={"studentId": str(outsider)}
    )

    assert (response.status_code, response.json()["code"]) == (422, "STUDENT_NOT_IN_EXAM_BRANCH")


def test_validation_errors_do_not_echo_submitted_values_into_logs(client, actors, caplog) -> None:
    response = client.put(
        f"{API}/admin/student-replicas",
        headers=actors.admin,
        json={"students": [{"studentId": "not-a-uuid", "studentNo": "SECRET42", "fullName": "Gizli Kişi"}]},
    )

    assert (response.status_code, response.json()["code"]) == (422, "VALIDATION_ERROR")
    logged = "\n".join(str(record.msg) for record in caplog.records)
    assert "request.invalid" in logged
    assert "SECRET42" not in logged
    assert "Gizli Kişi" not in logged


def test_student_number_of_another_student_is_rejected(client, actors) -> None:
    url = f"{API}/admin/student-replicas"
    first, second = uuid.uuid4(), uuid.uuid4()

    def students(student_id: uuid.UUID) -> dict:
        return {
            "students": [
                {
                    "studentId": str(student_id),
                    "studentNo": "4711",
                    "branchId": str(actors.branch_id),
                    "classId": str(uuid.uuid4()),
                    "fullName": "Test Öğrenci",
                }
            ]
        }

    created = client.put(url, headers=actors.admin, json=students(first))
    taken = client.put(url, headers=actors.admin, json=students(second))
    resent = client.put(url, headers=actors.admin, json=students(first))

    assert created.status_code == 200
    assert (taken.status_code, taken.json()["code"]) == (409, "STUDENT_NO_TAKEN")
    assert taken.json()["details"] == {"student_ids": [str(second)]}
    assert resent.status_code == 200


def test_unexpected_errors_become_logged_500s(client, actors, catalog, mocker, caplog) -> None:
    mocker.patch("app.services.catalog_service.list_exam_types", side_effect=RuntimeError("boom"))

    response = client.get(f"{API}/exam-types", headers=actors.teacher, params={})

    assert response.status_code == 500
    assert response.json()["code"] == "INTERNAL_ERROR"
    assert response.headers["x-request-id"] == response.json()["details"]["requestId"]
    assert any("request.failed" in str(record.msg) for record in caplog.records)


def test_every_rejection_is_logged_with_its_code(client, actors, caplog) -> None:
    client.get(f"{API}/mock-exams/{uuid.uuid4()}", headers=actors.admin)

    rejected = [
        record.msg
        for record in caplog.records
        if isinstance(record.msg, dict) and record.msg.get("event") == "request.rejected"
    ]
    assert rejected
    assert rejected[-1]["code"] == "MOCK_EXAM_NOT_FOUND"


def test_health_and_metrics(client) -> None:
    assert client.get("/health/live").json() == {"status": "ok"}
    ready = client.get("/health/ready")
    assert ready.status_code == 200
    assert ready.json()["checks"] == {"database": "ok", "cache": "disabled"}
    client.get(f"{API}/exam-types")
    metrics = client.get("/metrics/").text
    assert (
        'http_request_duration_seconds_count{method="GET",route="/api/v1/exam-types",status="401"}' in metrics
    )


def test_catalog_admin_endpoints(client, actors) -> None:
    exam_type = client.post(
        f"{API}/exam-types", headers=actors.admin, json={"code": "KPSS", "name": "KPSS", "wrongDivisor": "4"}
    )
    assert exam_type.status_code == 201, exam_type.text
    exam_type_id = exam_type.json()["id"]
    subject = client.post(
        f"{API}/exam-types/{exam_type_id}/subjects",
        headers=actors.admin,
        json={"code": "GY", "name": "Genel Yetenek"},
    )
    parent = client.post(
        f"{API}/subjects/{subject.json()['id']}/topics", headers=actors.admin, json={"name": "Sözel"}
    )
    child = client.post(
        f"{API}/subjects/{subject.json()['id']}/topics",
        headers=actors.admin,
        json={"name": "Paragraf", "parentTopicId": parent.json()["id"]},
    )
    assert child.json()["parentTopicId"] == parent.json()["id"]
    duplicate = client.post(
        f"{API}/exam-types", headers=actors.admin, json={"code": "KPSS", "name": "x", "wrongDivisor": "4"}
    )
    assert (duplicate.status_code, duplicate.json()["code"]) == (409, "EXAM_TYPE_CODE_EXISTS")

    score_type = client.post(
        f"{API}/exam-types/{exam_type_id}/score-types",
        headers=actors.admin,
        json={"code": "P3", "name": "P3"},
    )
    weights = client.put(
        f"{API}/score-types/{score_type.json()['id']}/weights",
        headers=actors.admin,
        json={"weights": [{"subjectId": subject.json()["id"], "weight": "0.5"}]},
    )
    assert weights.status_code == 200, weights.text
    foreign = client.put(
        f"{API}/score-types/{score_type.json()['id']}/weights",
        headers=actors.admin,
        json={"weights": [{"subjectId": str(uuid.uuid4()), "weight": "1"}]},
    )
    assert foreign.json()["code"] == "SUBJECT_NOT_IN_EXAM_TYPE"

    optical = client.post(
        f"{API}/optical-formats",
        headers=actors.admin,
        json={
            "code": "kpss-v1",
            "name": "KPSS",
            "fieldDefinition": {
                "studentNo": {"start": 0, "length": 8},
                "answerSegments": [{"start": 8, "length": 120}],
            },
        },
    )
    assert optical.status_code == 201, optical.text
    assert optical.json()["fieldDefinition"]["answerSegments"] == [{"start": 8, "length": 120}]
