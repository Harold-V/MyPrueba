#!/usr/bin/env bash
# Consistent copy of the SQLite database (safe while the app runs), keeping the newest 14.
# Runs before every deploy and, if you add the cron line from the guide, once a day.
set -euo pipefail
cd "$(dirname "$0")"
[ -f data/myprueba.db ] || exit 0
command -v sqlite3 >/dev/null || { echo "backup skipped: install sqlite3 (sudo apt install sqlite3)"; exit 0; }
mkdir -p backups
sqlite3 data/myprueba.db ".backup 'backups/myprueba-$(date -u +%Y%m%dT%H%M%SZ).db'"
find backups -name 'myprueba-*.db' | sort -r | tail -n +15 | xargs -r rm --
