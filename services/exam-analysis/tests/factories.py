"""Test data: a small exam type with two subjects and a six-question key in two booklets."""

import uuid
from dataclasses import dataclass
from decimal import Decimal

from sqlalchemy.orm import Session

from app.models import ExamType, OpticalFormat, ScoreType, ScoreTypeWeight, Subject, Topic


@dataclass(frozen=True)
class Catalog:
    exam_type_id: uuid.UUID
    math_id: uuid.UUID
    turkish_id: uuid.UUID
    numbers_id: uuid.UUID
    problems_id: uuid.UUID
    age_problems_id: uuid.UUID
    paragraph_id: uuid.UUID
    score_type_id: uuid.UUID
    optical_format_id: uuid.UUID

    def answer_key(self) -> list[dict[str, object]]:
        """Booklet A: 1-2 numbers, 3-4 age problems, 5-6 paragraph. Booklet B lists them in reverse."""
        booklet_a = [
            (self.math_id, self.numbers_id, "A"),
            (self.math_id, self.numbers_id, "B"),
            (self.math_id, self.age_problems_id, "C"),
            (self.math_id, self.age_problems_id, "D"),
            (self.turkish_id, self.paragraph_id, "E"),
            (self.turkish_id, self.paragraph_id, "A"),
        ]
        items = []
        for booklet, questions in (("A", booklet_a), ("B", list(reversed(booklet_a)))):
            for number, (subject, topic, answer) in enumerate(questions, start=1):
                items.append(
                    {
                        "booklet": booklet,
                        "questionNo": number,
                        "subjectId": str(subject),
                        "topicId": str(topic),
                        "correctAnswer": answer,
                    }
                )
        return items


def create_catalog(session: Session) -> Catalog:
    exam_type = ExamType(code="TST", name="Test Exam", wrong_divisor=Decimal(4))
    session.add(exam_type)
    session.flush()
    math = Subject(exam_type_id=exam_type.id, code="MAT", name="Matematik")
    turkish = Subject(exam_type_id=exam_type.id, code="TUR", name="Türkçe")
    session.add_all([math, turkish])
    session.flush()
    numbers = Topic(subject_id=math.id, name="Sayılar")
    problems = Topic(subject_id=math.id, name="Problemler")
    paragraph = Topic(subject_id=turkish.id, name="Paragraf")
    session.add_all([numbers, problems, paragraph])
    session.flush()
    age_problems = Topic(subject_id=math.id, name="Yaş Problemleri", parent_topic_id=problems.id)
    session.add(age_problems)
    score_type = ScoreType(exam_type_id=exam_type.id, code="TST", name="Test Score", base_score=Decimal(100))
    score_type.weights = [
        ScoreTypeWeight(subject_id=math.id, weight=Decimal(3)),
        ScoreTypeWeight(subject_id=turkish.id, weight=Decimal(2)),
    ]
    optical_format = OpticalFormat(
        code="test-fwf",
        name="Test format",
        field_definition={
            "encoding": "utf-8",
            "studentNo": {"start": 0, "length": 6},
            "booklet": {"start": 6, "length": 1},
            "answerSegments": [{"start": 7, "length": 6}],
            "blankChars": " ",
        },
    )
    session.add_all([score_type, optical_format])
    session.commit()
    return Catalog(
        exam_type_id=exam_type.id,
        math_id=math.id,
        turkish_id=turkish.id,
        numbers_id=numbers.id,
        problems_id=problems.id,
        age_problems_id=age_problems.id,
        paragraph_id=paragraph.id,
        score_type_id=score_type.id,
        optical_format_id=optical_format.id,
    )
