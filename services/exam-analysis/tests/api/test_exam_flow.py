"""End-to-end flow through the API and the worker on a real PostgreSQL."""

import uuid
from decimal import Decimal

import pytest
from sqlalchemy import select

from app.models import OutboxEvent

API = "/api/v1"
PERSONAL_DATA = ["ST7001", "ST7002", "ST7003", "ST7004", "ST9999", "Ali Yılmaz", "Ayşe Kaya", "Can Demir"]


@pytest.fixture
def students(client, actors, catalog) -> dict[str, uuid.UUID]:
    roster = {
        "ali": ("ST7001", "Ali Yılmaz"),
        "ayse": ("ST7002", "Ayşe Kaya"),
        "can": ("ST7003", "Can Demir"),
        "deniz": ("ST7004", "Deniz Ak"),
        "ece": ("ST7005", "Ece Su"),
        "fatma": ("ST7006", "Fatma Er"),
    }
    ids = {key: uuid.uuid4() for key in roster}
    class_id = uuid.uuid4()
    response = client.put(
        f"{API}/admin/student-replicas",
        headers=actors.admin,
        json={
            "students": [
                {
                    "studentId": str(ids[key]),
                    "studentNo": no,
                    "branchId": str(actors.branch_id),
                    "classId": str(class_id),
                    "fullName": name,
                }
                for key, (no, name) in roster.items()
            ]
        },
    )
    assert response.status_code == 200, response.text
    assert response.json() == {"upserted": 6}
    return ids


OPTICAL_FILE = "\n".join(
    [
        "ST7001AABCDEA",  # Ali: all correct
        "ST7002AABCD  ",  # Ayşe: math correct, Turkish blank
        "ST7003BAEDCAA",  # Can, booklet B: one math question wrong
        "ST7004AEEEEEE",  # Deniz: only question 5 correct
        "ST9999AABCDEA",  # unknown student number
        "ST7002AABCDEA",  # Ayşe again
        "ST",  # malformed
        "ST7001CABCDEA",  # booklet C does not exist
        "",
    ]
)


def create_exam(client, actors, catalog, scoring_source: str = "INSTITUTIONAL") -> str:
    response = client.post(
        f"{API}/mock-exams",
        headers=actors.admin,
        json={
            "examTypeId": str(catalog.exam_type_id),
            "name": "Deneme 1",
            "publisher": "Yayınevi",
            "examDate": "2026-10-10",
            "branchId": str(actors.branch_id),
            "scoringSource": scoring_source,
        },
    )
    assert response.status_code == 201, response.text
    assert response.json()["status"] == "DRAFT"
    return response.json()["id"]


def upload(client, actors, catalog, exam_id: str, content: str = OPTICAL_FILE):
    return client.post(
        f"{API}/mock-exams/{exam_id}/import-jobs",
        headers=actors.admin,
        data={"opticalFormatId": str(catalog.optical_format_id)},
        files={"file": ("deneme.txt", content.encode(), "text/plain")},
    )


def results_by_student(client, actors, exam_id: str) -> dict[str, dict]:
    response = client.get(f"{API}/mock-exams/{exam_id}/results", headers=actors.teacher)
    assert response.status_code == 200, response.text
    return {row["studentId"]: row for row in response.json()}


def outbox_events(session_factory) -> list[dict]:
    with session_factory() as session:
        return [
            event.envelope for event in session.scalars(select(OutboxEvent).order_by(OutboxEvent.created_at))
        ]


def unmatched(client, actors, job_id: str) -> dict[str, dict]:
    response = client.get(f"{API}/import-jobs/{job_id}/unmatched-rows", headers=actors.admin)
    assert response.status_code == 200, response.text
    return {row["reason"]: row for row in response.json()}


def test_full_exam_lifecycle(
    client, actors, catalog, students, processor, session_factory, student_headers, caplog
) -> None:
    exam_id = create_exam(client, actors, catalog)

    # The key must exist before files are accepted.
    assert upload(client, actors, catalog, exam_id).json()["code"] == "ANSWER_KEY_NOT_READY"
    key = client.put(
        f"{API}/mock-exams/{exam_id}/answer-key", headers=actors.admin, json={"items": catalog.answer_key()}
    )
    assert key.status_code == 200, key.text
    assert len(key.json()) == 12

    job = upload(client, actors, catalog, exam_id)
    assert job.status_code == 202, job.text
    assert job.json()["status"] == "QUEUED"
    job_id = job.json()["id"]

    assert processor.process_next() is True
    assert processor.process_next() is False

    job = client.get(f"{API}/import-jobs/{job_id}", headers=actors.teacher).json()
    assert (job["status"], job["totalRows"], job["succeededRows"], job["failedRows"]) == (
        "COMPLETED",
        8,
        4,
        4,
    )
    assert job["startedAt"] is not None
    assert job["finishedAt"] is not None

    results = results_by_student(client, actors, exam_id)
    nets = {key: Decimal(results[str(sid)]["net"]) for key, sid in students.items() if str(sid) in results}
    assert nets == {
        "ali": Decimal("6.00"),
        "ayse": Decimal("4.00"),
        "can": Decimal("4.75"),
        "deniz": Decimal("-0.25"),
    }
    ranks = {key: results[str(students[key])]["branchRank"] for key in nets}
    assert ranks == {"ali": 1, "can": 2, "ayse": 3, "deniz": 4}
    ali_score = results[str(students["ali"])]["scores"][0]
    assert ali_score["isEstimated"] is True
    assert ali_score["source"] == "INSTITUTIONAL"
    assert ali_score["branchRank"] == 1

    rows = unmatched(client, actors, job_id)
    assert set(rows) == {"STUDENT_NOT_FOUND", "DUPLICATE_STUDENT", "MALFORMED_LINE", "INVALID_BOOKLET"}

    # Malformed rows cannot be scored, only ignored.
    malformed = rows["MALFORMED_LINE"]["id"]
    rejected = client.post(
        f"{API}/unmatched-rows/{malformed}/match",
        headers=actors.admin,
        json={"studentId": str(students["ece"])},
    )
    assert (rejected.status_code, rejected.json()["code"]) == (422, "UNMATCHED_ROW_NOT_SCORABLE")
    ignored = client.post(f"{API}/unmatched-rows/{malformed}/ignore", headers=actors.admin)
    assert ignored.json()["status"] == "IGNORED"
    assert ignored.json()["resolvedBy"] == str(actors.admin_id)

    # A student who already has a result cannot get a second row.
    unknown = rows["STUDENT_NOT_FOUND"]["id"]
    second = client.post(
        f"{API}/unmatched-rows/{unknown}/match",
        headers=actors.admin,
        json={"studentId": str(students["ali"])},
    )
    assert (second.status_code, second.json()["code"]) == (409, "STUDENT_ALREADY_HAS_RESULT")

    matched = client.post(
        f"{API}/unmatched-rows/{unknown}/match",
        headers=actors.admin,
        json={"studentId": str(students["ece"])},
    )
    assert matched.status_code == 200, matched.text
    assert matched.json()["matchedStudentId"] == str(students["ece"])
    again = client.post(
        f"{API}/unmatched-rows/{unknown}/match",
        headers=actors.admin,
        json={"studentId": str(students["ece"])},
    )
    assert again.json()["code"] == "UNMATCHED_ROW_ALREADY_RESOLVED"

    results = results_by_student(client, actors, exam_id)
    assert results[str(students["ece"])]["branchRank"] == 1  # tied with Ali
    assert results[str(students["ali"])]["branchRank"] == 1
    assert outbox_events(session_factory) == []  # not published yet: no events

    # Students see nothing before publication.
    assert (
        client.get(
            f"{API}/students/{students['ali']}/results", headers=student_headers(students["ali"])
        ).json()
        == []
    )

    published = client.post(f"{API}/mock-exams/{exam_id}/publish", headers=actors.admin)
    assert published.status_code == 200, published.text
    assert published.json()["status"] == "PUBLISHED"
    events = outbox_events(session_factory)
    assert [event["eventType"] for event in events] == ["ExamResultsPublished"]
    assert set(events[0]["data"]["studentIds"]) == {
        str(students[k]) for k in ("ali", "ayse", "can", "deniz", "ece")
    }
    assert (
        client.post(f"{API}/mock-exams/{exam_id}/publish", headers=actors.admin).json()["code"]
        == "EXAM_ALREADY_PUBLISHED"
    )
    assert upload(client, actors, catalog, exam_id).json()["code"] == "EXAM_ALREADY_PUBLISHED"

    # Late matching on a published exam announces only that student.
    duplicate = rows["DUPLICATE_STUDENT"]["id"]
    late = client.post(
        f"{API}/unmatched-rows/{duplicate}/match",
        headers=actors.admin,
        json={"studentId": str(students["fatma"])},
    )
    assert late.status_code == 200, late.text
    events = outbox_events(session_factory)
    assert [event["eventType"] for event in events] == ["ExamResultsPublished", "StudentResultAdded"]
    assert events[1]["data"]["studentId"] == str(students["fatma"])

    # Weak topics need the branch setting.
    deniz = students["deniz"]
    missing = client.get(f"{API}/students/{deniz}/weak-topics", headers=student_headers(deniz))
    assert (missing.status_code, missing.json()["code"]) == (409, "WEAK_TOPIC_SETTING_MISSING")
    setting = client.put(
        f"{API}/branches/{actors.branch_id}/weak-topic-setting",
        headers=actors.admin,
        json={"windowSize": 3, "successThreshold": "0.6"},
    )
    assert setting.status_code == 200, setting.text
    report = client.get(f"{API}/students/{deniz}/weak-topics", headers=student_headers(deniz)).json()
    weak = {topic["topicName"]: Decimal(topic["successRate"]) for topic in report["weakTopics"]}
    assert weak == {"Sayılar": 0, "Yaş Problemleri": 0, "Problemler": 0, "Paragraf": Decimal("0.5")}
    assert list(weak)[-1] == "Paragraf"  # weakest first
    ali = students["ali"]
    assert (
        client.get(f"{API}/students/{ali}/weak-topics", headers=student_headers(ali)).json()["weakTopics"]
        == []
    )

    # Student views.
    own = client.get(f"{API}/students/{ali}/results", headers=student_headers(ali)).json()
    assert [Decimal(row["net"]) for row in own] == [Decimal("6.00")]
    detail = client.get(f"{API}/students/{ali}/results/{exam_id}", headers=student_headers(ali)).json()
    assert {subject["subjectName"]: Decimal(subject["net"]) for subject in detail["subjects"]} == {
        "Matematik": Decimal("4.00"),
        "Türkçe": Decimal("2.00"),
    }
    progress = client.get(
        f"{API}/students/{ali}/progress",
        params={"examTypeId": str(catalog.exam_type_id)},
        headers=student_headers(ali),
    ).json()
    assert [Decimal(point["net"]) for point in progress["points"]] == [Decimal("6.00")]
    other = client.get(f"{API}/students/{deniz}/results", headers=student_headers(ali))
    assert (other.status_code, other.json()["code"]) == (403, "STUDENT_NOT_ALLOWED")

    # Cancelling question 6 of booklet A removes it from everybody who used booklet A.
    key_items = client.get(f"{API}/mock-exams/{exam_id}/answer-key", headers=actors.teacher).json()
    q6 = next(item for item in key_items if item["booklet"] == "A" and item["questionNo"] == 6)
    cancelled = client.post(f"{API}/mock-exams/{exam_id}/answer-key/{q6['id']}/cancel", headers=actors.admin)
    assert cancelled.status_code == 200, cancelled.text
    results = results_by_student(client, actors, exam_id)
    assert Decimal(results[str(ali)]["net"]) == Decimal("5.00")
    assert Decimal(results[str(students["can"])]["net"]) == Decimal("4.75")  # booklet B untouched
    assert [event["eventType"] for event in outbox_events(session_factory)] == [
        "ExamResultsPublished",
        "StudentResultAdded",
    ]

    analysis = client.get(f"{API}/mock-exams/{exam_id}/topic-analysis", headers=actors.teacher).json()
    assert {item["topicName"] for item in analysis} == {"Sayılar", "Yaş Problemleri", "Paragraf"}

    # KVKK: no student number, name or raw optical line reached the logs.
    logged = "\n".join(str(record.msg) for record in caplog.records)
    assert "import_job.completed" in logged
    for value in PERSONAL_DATA:
        assert value not in logged


def test_national_scoring_waits_for_admin_statistics(client, actors, catalog, students, processor) -> None:
    exam_id = create_exam(client, actors, catalog, scoring_source="NATIONAL")
    client.put(
        f"{API}/mock-exams/{exam_id}/answer-key", headers=actors.admin, json={"items": catalog.answer_key()}
    )
    upload(client, actors, catalog, exam_id, content="ST7001AABCDEA\nST7002AABCD  \n")
    processor.process_next()

    before = results_by_student(client, actors, exam_id)[str(students["ali"])]
    assert Decimal(before["net"]) == Decimal("6.00")
    assert before["scores"][0]["estimatedScore"] is None  # net and analysis visible, score not yet

    teacher_attempt = client.put(
        f"{API}/mock-exams/{exam_id}/statistics/national",
        headers=actors.teacher,
        json={"items": []},
    )
    assert teacher_attempt.status_code == 403
    entered = client.put(
        f"{API}/mock-exams/{exam_id}/statistics/national",
        headers=actors.admin,
        json={
            "items": [
                {"subjectId": str(catalog.math_id), "mean": "2", "stdDev": "1", "participantCount": 150000},
                {
                    "subjectId": str(catalog.turkish_id),
                    "mean": "1",
                    "stdDev": "0.5",
                    "participantCount": 150000,
                },
            ]
        },
    )
    assert entered.status_code == 200, entered.text

    after = results_by_student(client, actors, exam_id)[str(students["ali"])]["scores"][0]
    # math SP = 50 + 10 * (4 - 2) / 1 = 70, Turkish SP = 50 + 10 * (2 - 1) / 0.5 = 70
    assert Decimal(after["estimatedScore"]) == Decimal("100") + 3 * 70 + 2 * 70
    assert after["source"] == "NATIONAL"


def test_answer_key_validation(client, actors, catalog) -> None:
    exam_id = create_exam(client, actors, catalog)
    items = catalog.answer_key()

    gap = [item for item in items if not (item["booklet"] == "B" and item["questionNo"] == 3)]
    response = client.put(f"{API}/mock-exams/{exam_id}/answer-key", headers=actors.admin, json={"items": gap})
    assert (response.status_code, response.json()["code"]) == (422, "ANSWER_KEY_INCOMPLETE")

    wrong_topic = [{**items[0], "topicId": str(catalog.paragraph_id)}, *items[1:]]
    response = client.put(
        f"{API}/mock-exams/{exam_id}/answer-key", headers=actors.admin, json={"items": wrong_topic}
    )
    assert (response.status_code, response.json()["code"]) == (422, "TOPIC_NOT_IN_SUBJECT")

    duplicate = [*items, items[0]]
    response = client.put(
        f"{API}/mock-exams/{exam_id}/answer-key", headers=actors.admin, json={"items": duplicate}
    )
    assert response.json()["code"] == "DUPLICATE_QUESTION"
