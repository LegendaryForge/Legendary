#!/usr/bin/env bash
# Brings the test server UP and LEAVES IT RUNNING, with a console FIFO that
# survives across commands so probes can be fired while a client is connected.
#
# Differs from the drive-*.sh scripts on purpose: those boot, fire one probe and
# stop, which is wrong when a human needs to connect a client in between.
#
# The FIFO holder is `tail -f /dev/null`, not `sleep N` -- a sleep-based holder
# expired mid-session once and the console silently stopped accepting commands,
# presenting as a hung server.
#
#   bash scripts/live-probes/server-up.sh
#   echo signalprobe > .scratch/hytale-server/console   # fire a command
#   echo stop        > .scratch/hytale-server/console   # shut it down
#
# The FIFO is created inside the harness directory, not next to this script, so
# the paths above are relative to the repository root. Use $HYTALE_TEST_SERVER if
# the harness lives elsewhere.
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
OUT="live-session.log"
FIFO="console"

rm -f "$FIFO"
mkfifo "$FIFO"
# Non-expiring holder: keeps the FIFO open so writes never block or EOF the server.
tail -f /dev/null > "$FIFO" &
HOLDER=$!
echo "$HOLDER" > .holder.pid

: > "$OUT"
"$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
  --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$FIFO" > "$OUT" 2>&1 &
PID=$!
echo "$PID" > .server.pid

for i in $(seq 1 300); do
  grep -q "Universe ready!" "$OUT" && { echo "READY after ${i}s"; break; }
  kill -0 "$PID" 2>/dev/null || { echo "SERVER DIED EARLY -- see $OUT"; exit 1; }
  sleep 1
done

# Liveness by PORT, never by process name: pgrep -f matches the shell running it.
# UDP, not TCP -- Hytale's transport is QUIC, so `ss -ltn` reports NOT LISTENING on a
# perfectly healthy server. That false negative cost a round trip on 2026-10-21.
ss -lun | grep -q ':5520' && echo "PORT 5520 LISTENING (udp/QUIC)" || echo "PORT 5520 NOT LISTENING"
echo "SERVER_UP_VERDICT: READY | pid=$PID | connect a client to 127.0.0.1:5520"
