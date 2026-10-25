import pandas as pd


def rank_descending(values: pd.Series) -> pd.Series:
    """Competition ranking (1, 2, 2, 4); missing values stay unranked."""
    return values.rank(method="min", ascending=False, na_option="keep").astype("Int64")


def rank_descending_within(frame: pd.DataFrame, value_column: str, group_column: str) -> pd.Series:
    return (
        frame.groupby(group_column)[value_column]
        .rank(method="min", ascending=False, na_option="keep")
        .astype("Int64")
    )
