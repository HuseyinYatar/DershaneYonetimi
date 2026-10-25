import uuid
from collections.abc import Collection
from dataclasses import dataclass

import numpy as np
import pandas as pd

from app.models.enums import UnmatchedReason


@dataclass(frozen=True)
class RowClassification:
    """matched: sheets plus student_id; unmatched: sheets plus reason (UnmatchedReason value)."""
    matched: pd.DataFrame
    unmatched: pd.DataFrame


def classify_rows( sheets: pd.DataFrame, *, valid_booklets: Collection[str], student_ids_by_no: pd.Series, existing_student_ids: Collection[uuid.UUID],
) -> RowClassification:
    """Decides which optical rows can be scored.

    Reasons are checked in priority order: malformed line, unknown booklet, student number not in
    the exam's branch, student already scored (earlier row in this file or an existing result).
    """
    student_id = sheets["student_no"].map(student_ids_by_no)
    is_malformed = sheets["is_malformed"].to_numpy(dtype=bool)
    invalid_booklet = ~sheets["booklet"].isin(list(valid_booklets)).to_numpy()
    not_found = student_id.isna().to_numpy()

    scorable_so_far = ~is_malformed & ~invalid_booklet & ~not_found
    candidates = student_id.where(scorable_so_far)
    duplicate = (
        (candidates.duplicated(keep="first") & candidates.notna())
        | candidates.isin(list(existing_student_ids))
    ).to_numpy()

    reason = np.select(
        [is_malformed, invalid_booklet, not_found, duplicate],
        [
            UnmatchedReason.MALFORMED_LINE.value,
            UnmatchedReason.INVALID_BOOKLET.value,
            UnmatchedReason.STUDENT_NOT_FOUND.value,
            UnmatchedReason.DUPLICATE_STUDENT.value,
        ],
        default="",
    )
    is_matched = reason == ""
    matched = sheets[is_matched].assign(student_id=student_id[is_matched])
    unmatched = sheets[~is_matched].assign(reason=reason[~is_matched])
    return RowClassification(matched=matched, unmatched=unmatched)
