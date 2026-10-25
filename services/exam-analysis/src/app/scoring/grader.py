import numpy as np
import pandas as pd

from app.models.enums import AnswerStatus

ANSWER_KEY_COLUMNS = [
    "answer_key_item_id",
    "booklet",
    "question_no",
    "subject_id",
    "topic_id",
    "correct_answer",
    "is_cancelled",
]

def explode_answers(sheets: pd.DataFrame, question_count: int, blank_chars: str) -> pd.DataFrame:
    """Turns each sheet's answer string into one row per question.

    sheets: indexed by sheet_key, columns booklet and answers (strings of exactly question_count
    characters). Returns sheet_key, booklet, question_no, given_answer (None when blank).
    """
    sheet_count = len(sheets)
    matrix = (
        sheets["answers"]
        .to_numpy(dtype=f"<U{question_count}")
        .view("<U1")
        .reshape(sheet_count, question_count)
    )
    given = np.char.upper(matrix.ravel())
    is_blank = np.isin(given, list(blank_chars)) | (given == "")
    long = pd.DataFrame(
        {
            "sheet_key": np.repeat(sheets.index.to_numpy(), question_count),
            "booklet": np.repeat(sheets["booklet"].to_numpy(), question_count),
            "question_no": np.tile(np.arange(1, question_count + 1), sheet_count),
            "given_answer": given.astype(object),
        }
    )
    long.loc[is_blank, "given_answer"] = None
    return long


def assign_status(frame: pd.DataFrame) -> np.ndarray:
    """frame: given_answer (None = blank), correct_answer, is_cancelled."""
    return np.select(
        [
            frame["is_cancelled"].to_numpy(dtype=bool),
            frame["given_answer"].isna().to_numpy(),
            (frame["given_answer"] == frame["correct_answer"]).to_numpy(dtype=bool),
        ],
        [AnswerStatus.CANCELLED.value, AnswerStatus.BLANK.value, AnswerStatus.CORRECT.value],
        default=AnswerStatus.WRONG.value,
    ).astype(object)


def grade(long_answers: pd.DataFrame, answer_key: pd.DataFrame) -> pd.DataFrame:
    """Joins answers with the key on (booklet, question_no) and assigns a status per answer.

    Answer columns beyond the key are dropped (forms often have more bubbles than questions).
    """
    graded = long_answers.merge(answer_key[ANSWER_KEY_COLUMNS], on=["booklet", "question_no"], how="inner")
    graded["status"] = assign_status(graded)
    return graded


def regrade(stored_answers: pd.DataFrame, answer_key: pd.DataFrame) -> pd.DataFrame:
    """Re-grades persisted answers (sheet_key, answer_key_item_id, given_answer) after a key change."""
    graded = stored_answers.merge(
        answer_key[ANSWER_KEY_COLUMNS].drop(columns=["booklet"]), on="answer_key_item_id", how="inner"
    )
    graded["status"] = assign_status(graded)
    return graded
