#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="/opt/live-rec-pi"
SERVICE="live-rec-api"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Installing $SERVICE..."

pip3 install -r "$SCRIPT_DIR/requirements.txt" --quiet

mkdir -p "$INSTALL_DIR"
rsync -a --exclude='.git' "$SCRIPT_DIR/../" "$INSTALL_DIR/"

cp "$SCRIPT_DIR/$SERVICE.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable "$SERVICE"
systemctl start "$SERVICE"

echo "Done. Service is running and will start automatically on boot."
echo "Check status with: systemctl status $SERVICE"
