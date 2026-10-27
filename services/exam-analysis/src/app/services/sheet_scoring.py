from dataclasses import dataclass

import pandas as pd

from app.schemas.optical import OpticalFieldDefinition
from app.scoring.aggregation import aggregate_subjects, aggregate_topics, aggregate_totals
from app.scoring.grader import explode_answers, grade


@dataclass(frozen=True)
class ScoredSheets:
    """All frames are keyed by sheet_key (the sheet's index in the matched frame)."""
    graded: pd.DataFrame
    subjects: pd.DataFrame
    topics: pd.DataFrame
    totals: pd.DataFrame


def score_sheets( sheets: pd.DataFrame, answer_key: pd.DataFrame, definition: OpticalFieldDefinition, wrong_divisor: float) -> ScoredSheets:
    """Grades optical rows against the key and sums them up per subject, topic and student.

    Returns the graded answers and the three totals tables. Shared by the import worker and late
    matching so both produce identical results.
    """
    long_answers = explode_answers(sheets, definition.question_count, definition.blank_chars)
    graded = grade(long_answers, answer_key)
    subjects = aggregate_subjects(graded, wrong_divisor)
    return ScoredSheets(
        graded=graded, subjects=subjects, topics=aggregate_topics(graded), totals=aggregate_totals(subjects)
    )
