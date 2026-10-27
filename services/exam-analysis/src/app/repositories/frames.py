from collections.abc import Sequence
from typing import Any

import pandas as pd
from sqlalchemy import Executable
from sqlalchemy.orm import Session


def read_frame(session: Session, statement: Executable, columns: Sequence[str]) -> pd.DataFrame:
    rows = session.execute(statement).all()
    return pd.DataFrame.from_records([tuple(row) for row in rows], columns=list(columns))

# kod içinde dataframe veye pandas nesnesi olarak çalışıyoru ya dbye giderken sqlalchemy sozklk listesi istiyor onu convert yapiyor
def to_records(frame: pd.DataFrame) -> list[dict[str, Any]]:
    """Native Python values with None for missing ones, ready for executemany (psycopg cannot
    adapt numpy scalars)."""
    return frame.astype(object).where(frame.notna(), None).to_dict("records")
