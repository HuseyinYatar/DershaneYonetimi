# One image, two entrypoints:
#   API:        uvicorn app.main:create_app --factory --host 0.0.0.0 --port 8000   (default)
#   Migrations: alembic upgrade head   (Kubernetes Job / init container)
# Uploads larger than 1 MB are spooled to /tmp while they are parsed; with a read-only root
# filesystem in Kubernetes, /tmp needs an emptyDir.

FROM python:3.14-slim AS builder
COPY --from=ghcr.io/astral-sh/uv:0.13.1 /uv /bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
RUN uv sync --frozen --no-dev

FROM python:3.14-slim
# Debian security fixes the base image does not carry yet (the Trivy step fails on HIGH/CRITICAL).
# pip (with the urllib3, msgpack and setuptools code vendored in it) only installs packages; the
# service runs from /app/.venv, so the base interpreter's pip and ensurepip are removed.
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* \
    && python -m pip uninstall -y pip \
    && rm -rf /usr/local/lib/python3.14/ensurepip \
    && useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=builder /app/.venv /app/.venv
COPY --from=builder /app/src /app/src
COPY alembic.ini ./
COPY migrations ./migrations
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1
USER 10001
EXPOSE 8000
CMD ["uvicorn", "app.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
