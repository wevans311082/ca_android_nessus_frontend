FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HOST=0.0.0.0 \
    PORT=8834 \
    DATABASE_PATH=/data/nessus_mock.db

RUN mkdir -p /data

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8834

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/server/status" || exit 1

# gunicorn for production-style demo; single worker keeps in-memory store shared
CMD gunicorn --bind "${HOST}:${PORT}" --workers 1 --threads 4 --timeout 120 "app:app"
