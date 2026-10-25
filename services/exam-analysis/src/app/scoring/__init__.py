"""Pure, database-free scoring functions built on pandas.

Everything here is vectorised (no per-row Python loops over sheet data) and returns data plus
diagnostics; callers in app.services log the diagnostics and persist the frames.
"""
