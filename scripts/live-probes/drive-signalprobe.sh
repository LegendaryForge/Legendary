#!/usr/bin/env bash
# Boots the test server headless, waits for readiness, runs /signalprobe, stops.
# Pattern copied from drive-beamprobe.sh (2026-10-20).
#
# Headless is deliberate here. /signalprobe surveys loaded storage at offsets from
# the origin rather than only at player positions, so it produces a real answer with
# no client attached -- which matters because a client login needs browser-approved
# server auth that a FIFO console cannot do.
set -uo pipefail
# The harness directory stays OUT of git: it holds auth.enc and generated
# universe/ data. Only this driver is tracked. Override with HYTALE_TEST_SERVER.
SERVER_DIR="${HYTALE_TEST_SERVER:-$(cd "$(dirname "$0")/../../.scratch/hytale-server" && pwd)}"
if [ ! -f "$SERVER_DIR/run-server.sh" ]; then
  echo "No test-server harness at $SERVER_DIR -- see scripts/live-probes/README.md" >&2
  exit 1
fi
cd "$SERVER_DIR"
GAME="$HOME/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest"
JAVA="$HOME/.gradle/jdks/jetbrains_s_r_o_-25-amd64-linux.2/bin/java"
OUT="signalprobe-run.log"
FIFO="$(mktemp -u /tmp/hysignal.XXXXXX)"
mkfifo "$FIFO"

: > "$OUT"
"$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
  --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$FIFO" > "$OUT" 2>&1 &
PID=$!
exec 3>"$FIFO"

READY=no
for i in $(seq 1 300); do
  grep -q "Universe ready!" "$OUT" && { READY=yes; break; }
  kill -0 "$PID" 2>/dev/null || { echo "SERVER DIED EARLY"; break; }
  sleep 1
done
echo "READY=$READY after ${i}s"
sleep 5

echo "signalprobe" >&3
sleep 15
echo "/signalprobe" >&3
sleep 15
echo "stop" >&3
sleep 15
kill "$PID" 2>/dev/null
wait "$PID" 2>/dev/null
exec 3>&-
rm -f "$FIFO"
echo "DRIVER_VERDICT: COMPLETE"
