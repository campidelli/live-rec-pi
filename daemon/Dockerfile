FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    alsa-utils \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY daemon/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY daemon/ .

ENV SOCKET_PATH=/run/live-rec/daemon.sock
ENV RECORDINGS_DIR=/recordings

CMD ["python3", "-m", "daemon.main", "--config-dir", "/app/config"]
