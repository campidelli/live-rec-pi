#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="/opt/live-rec-pi"
SERVICE="live-rec-daemon"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Installing $SERVICE..."

# install Python dependencies
pip3 install -r "$SCRIPT_DIR/requirements.txt" --quiet

# copy project to install dir
mkdir -p "$INSTALL_DIR"
rsync -a --exclude='.git' "$SCRIPT_DIR/../" "$INSTALL_DIR/"

# install and enable systemd unit
cp "$SCRIPT_DIR/$SERVICE.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable "$SERVICE"
systemctl start "$SERVICE"

echo "Done. Service is running and will start automatically on boot."
echo "Check status with: systemctl status $SERVICE"
