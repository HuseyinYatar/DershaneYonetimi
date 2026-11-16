"""Operational commands: `python -m app.cli <command>`.

seed-catalog [PATH]     load exam types, subjects, topics, score types and optical formats (idempotent)
seed-students PATH      load the local student copy from a JSON list (development only)
rescore                 recompute statistics, estimated scores and ranks of every exam with results
                        (after the score type coefficients change); oldest first, so AYT/YDT link
                        to TYT results that are already rescored

AWS resources (bucket, topic, queues) are infrastructure: infra/terraform creates them, in Floci
locally and on AWS.
"""

import argparse
import json
import sys
from decimal import Decimal
from pathlib import Path
from typing import Any

import structlog
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.cache import build_cache
from app.config import get_settings
from app.db import get_session_factory
from app.models import (
    ExamType,
    MockExam,
    OpticalFormat,
    ScoreType,
    ScoreTypeWeight,
    StudentResult,
    Subject,
    Topic,
)
from app.observability.logging import configure_logging
from app.repositories import exams as exams_repo
from app.repositories import results as results_repo
from app.schemas.optical import OpticalFieldDefinition
from app.schemas.replicas import StudentReplicaIn
from app.services import replica_service, scoring_service

logger = structlog.get_logger(__name__)

DEFAULT_CATALOG = Path(__file__).resolve().parents[2] / "seed" / "catalog.json"


def seed_catalog(session: Session, catalog: dict[str, Any]) -> None:
    for exam_type_data in catalog["examTypes"]:
        exam_type = _upsert_exam_type(session, exam_type_data)
        subjects = {
            subject_data["code"]: _upsert_subject(session, exam_type, subject_data)
            for subject_data in exam_type_data["subjects"]
        }
        for score_type_data in exam_type_data["scoreTypes"]:
            _upsert_score_type(session, exam_type, subjects, score_type_data)
    for format_data in catalog["opticalFormats"]:
        definition = OpticalFieldDefinition.model_validate(format_data["fieldDefinition"]).model_dump(
            by_alias=True
        )
        optical_format = session.scalar(
            select(OpticalFormat).where(OpticalFormat.code == format_data["code"])
        )
        if optical_format is None:
            optical_format = OpticalFormat(code=format_data["code"])
            session.add(optical_format)
        optical_format.name = format_data["name"]
        optical_format.field_definition = definition
    session.commit()
    logger.info(
        "seed.catalog_loaded",
        exam_types=len(catalog["examTypes"]),
        optical_formats=len(catalog["opticalFormats"]),
    )


def _upsert_exam_type(session: Session, data: dict[str, Any]) -> ExamType:
    exam_type = session.scalar(select(ExamType).where(ExamType.code == data["code"]))
    if exam_type is None:
        exam_type = ExamType(code=data["code"])
        session.add(exam_type)
    exam_type.name = data["name"]
    exam_type.wrong_divisor = Decimal(str(data["wrongDivisor"]))
    session.flush()
    return exam_type


def _upsert_subject(session: Session, exam_type: ExamType, data: dict[str, Any]) -> Subject:
    subject = session.scalar(
        select(Subject).where(Subject.exam_type_id == exam_type.id, Subject.code == data["code"])
    )
    if subject is None:
        subject = Subject(exam_type_id=exam_type.id, code=data["code"])
        session.add(subject)
    subject.name = data["name"]
    session.flush()
    for topic_data in data.get("topics", []):
        _upsert_topic(session, subject, topic_data, parent=None)
    return subject


def _upsert_topic(session: Session, subject: Subject, data: dict[str, Any], parent: Topic | None) -> None:
    topic = session.scalar(select(Topic).where(Topic.subject_id == subject.id, Topic.name == data["name"]))
    if topic is None:
        topic = Topic(subject_id=subject.id, name=data["name"])
        session.add(topic)
    topic.parent_topic_id = None if parent is None else parent.id
    session.flush()
    for child in data.get("children", []):
        _upsert_topic(session, subject, child, parent=topic)


def _upsert_score_type(
    session: Session, exam_type: ExamType, subjects: dict[str, Subject], data: dict[str, Any]
) -> None:
    score_type = session.scalar(
        select(ScoreType).where(ScoreType.exam_type_id == exam_type.id, ScoreType.code == data["code"])
    )
    if score_type is None:
        score_type = ScoreType(exam_type_id=exam_type.id, code=data["code"])
        session.add(score_type)
    score_type.name = data["name"]
    score_type.base_score = _decimal(data.get("baseScore"))
    score_type.coefficient_year = data.get("coefficientYear")
    score_type.minimum_net = _decimal(data.get("minimumNet"))
    score_type.max_score = _decimal(data.get("maxScore"))
    # [["TUR"], ["MAT"]]: each inner list is one ÖSYM test for the minimum-net rule.
    groups = {
        code: number
        for number, group in enumerate(data.get("minimumNetGroups", []), start=1)
        for code in group
    }
    score_type.weights = [
        ScoreTypeWeight(
            subject_id=_resolve_subject(session, subjects, code).id,
            weight=Decimal(str(weight)),
            minimum_net_group=groups.get(code),
        )
        for code, weight in data["weights"].items()
    ]
    session.flush()
    if not data.get("weightsVerified", False):
        logger.warning(
            "seed.weights_unverified",
            exam_type=exam_type.code,
            score_type=score_type.code,
            reason="weights must be checked against the ÖSYM/MEB guide (open decision in CLAUDE.md)",
        )


def _decimal(value: Any) -> Decimal | None:
    return None if value is None else Decimal(str(value))


def _resolve_subject(session: Session, own: dict[str, Subject], code: str) -> Subject:
    """Finds a catalog subject code: MAT is a subject of the score type's own exam type, TYT:MAT
    one of another exam type, which must come earlier in the catalog file."""
    if ":" not in code:
        return own[code]
    exam_type_code, subject_code = code.split(":", 1)
    subject = session.scalar(
        select(Subject)
        .join(ExamType, ExamType.id == Subject.exam_type_id)
        .where(ExamType.code == exam_type_code, Subject.code == subject_code)
    )
    if subject is None:
        raise ValueError(f"unknown subject {code}: seed its exam type earlier in the catalog")
    return subject


def rescore(session: Session) -> int:
    """Recomputes every exam that has results, one transaction per exam (the exam row is locked while
    its scores are rewritten), oldest first so an AYT links to TYT results already rescored."""
    settings = get_settings()
    cache = build_cache(settings.redis_url, settings.cache_ttl_seconds)
    exam_ids = session.scalars(
        select(MockExam.id)
        .where(MockExam.id.in_(select(StudentResult.mock_exam_id)))
        .order_by(MockExam.exam_date, MockExam.created_at)
    ).all()
    for exam_id in exam_ids:
        exam = exams_repo.get_mock_exam(session, exam_id, for_update=True)
        scoring_service.recalculate_exam(session, exam, refresh_institutional=True)
        student_ids = sorted(results_repo.existing_student_ids(session, exam.id))
        session.commit()
        cache.invalidate_exam(exam.id, exam.exam_type_id, student_ids)
    logger.info("rescore.completed", exams=len(exam_ids))
    return len(exam_ids)


def seed_students(session: Session, students: list[dict[str, Any]]) -> None:
    replicas = [StudentReplicaIn.model_validate(student) for student in students]
    count = replica_service.upsert(session, replicas)
    session.commit()
    logger.info("seed.students_loaded", students=count)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.cli")
    commands = parser.add_subparsers(dest="command", required=True)
    catalog_parser = commands.add_parser("seed-catalog")
    catalog_parser.add_argument("path", nargs="?", type=Path, default=DEFAULT_CATALOG)
    students_parser = commands.add_parser("seed-students")
    students_parser.add_argument("path", type=Path)
    commands.add_parser("rescore")
    args = parser.parse_args(argv)

    settings = get_settings()
    configure_logging(settings.log_level, f"{settings.service_name}-cli", settings.environment)
    with get_session_factory()() as session:
        if args.command == "rescore":
            rescore(session)
            return 0
        data = json.loads(args.path.read_text(encoding="utf-8"))
        if args.command == "seed-catalog":
            seed_catalog(session, data)
        else:
            seed_students(session, data)
    return 0


if __name__ == "__main__":
    sys.exit(main())
