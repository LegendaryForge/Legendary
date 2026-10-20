#!/usr/bin/env bash
# Two boot cycles. Cycle 1 registers points; cycle 2 reports what survived the
# restart BEFORE registering anything, which is the persistence answer.
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

cycle() {
  local out="$1"
  local fifo; fifo="$(mktemp -u /tmp/hypoint.XXXXXX)"
  mkfifo "$fifo"
  : > "$out"
  "$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
    --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$fifo" > "$out" 2>&1 &
  local pid=$!
  exec 3>"$fifo"
  local ready=no i
  for i in $(seq 1 300); do
    grep -q "Universe ready!" "$out" && { ready=yes; break; }
    kill -0 "$pid" 2>/dev/null || { echo "  SERVER DIED EARLY"; break; }
    sleep 1
  done
  echo "  READY=$ready after ${i}s"
  sleep 5
  echo "pointprobe" >&3
  sleep 20
  echo "stop" >&3
  sleep 20
  kill "$pid" 2>/dev/null
  wait "$pid" 2>/dev/null
  exec 3>&-
  rm -f "$fifo"
}

echo "CYCLE 1 (register)"
cycle "pointprobe-run1.log"
echo "CYCLE 2 (verify persistence)"
cycle "pointprobe-run2.log"
echo "DRIVER_VERDICT: COMPLETE"
