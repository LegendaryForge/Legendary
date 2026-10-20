#!/usr/bin/env bash
# Boots the test server headless, waits for readiness, runs /blockprobe, stops.
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
OUT="prefabprobe-run.log"
FIFO="$(mktemp -u /tmp/hyprobe.XXXXXX)"
mkfifo "$FIFO"

: > "$OUT"
"$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
  --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$FIFO" > "$OUT" 2>&1 &
PID=$!
exec 3>"$FIFO"

for i in $(seq 1 180); do
  grep -q "Universe ready!" "$OUT" && break
  kill -0 "$PID" 2>/dev/null || { echo "SERVER DIED EARLY"; break; }
  sleep 1
done
sleep 5

echo "prefabprobe" >&3
sleep 25
echo "stop" >&3
sleep 15
kill "$PID" 2>/dev/null
wait "$PID" 2>/dev/null
exec 3>&-
rm -f "$FIFO"
echo "DRIVER_VERDICT: COMPLETE"
