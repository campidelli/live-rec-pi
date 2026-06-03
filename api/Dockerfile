# Stage 1: build the web UI
FROM node:20-alpine AS web-builder
WORKDIR /web
COPY web/package.json web/package-lock.json* ./
RUN npm install --frozen-lockfile
COPY web/ ./
RUN npm run build

# Stage 2: run the API with the built web UI
FROM python:3.11-slim

WORKDIR /app

COPY api/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY api/src/ src/
COPY docker/api.yaml config/api.yaml
COPY --from=web-builder /web/dist web/dist

ENV PYTHONPATH=/app/src

CMD ["python3", "-m", "api.main", "--config-dir", "/app/config"]
