"""Operational commands: `python -m app.cli <command>`.

seed-catalog [PATH]     load exam types, subjects, topics, score types and optical formats (idempotent)
seed-students PATH      load the local student copy from a JSON list (development only)

AWS resources (bucket, topic, queues) are infrastructure: local/floci/init.sh creates them in Floci,
Terraform on AWS.
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

from app.config import get_settings
from app.db import get_session_factory
from app.models import ExamType, OpticalFormat, ScoreType, ScoreTypeWeight, Subject, Topic
from app.observability.logging import configure_logging
from app.schemas.optical import OpticalFieldDefinition
from app.schemas.replicas import StudentReplicaIn
from app.services import replica_service

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
    score_type.base_score = None if data.get("baseScore") is None else Decimal(str(data["baseScore"]))
    score_type.weights = [
        ScoreTypeWeight(subject_id=subjects[code].id, weight=Decimal(str(weight)))
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
    args = parser.parse_args(argv)

    settings = get_settings()
    configure_logging(settings.log_level, f"{settings.service_name}-cli", settings.environment)
    with get_session_factory()() as session:
        data = json.loads(args.path.read_text(encoding="utf-8"))
        if args.command == "seed-catalog":
            seed_catalog(session, data)
        else:
            seed_students(session, data)
    return 0


if __name__ == "__main__":
    sys.exit(main())
