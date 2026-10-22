#!/usr/bin/env bash
# Brings the test server UP and LEAVES IT RUNNING, with a console FIFO that
# survives across commands so probes can be fired while a client is connected.
#
# Differs from the drive-*.sh scripts on purpose: those boot, fire one probe and
# stop, which is wrong when a human needs to connect a client in between.
#
# The FIFO holder needs BOTH properties, and each was learned from its own failure:
#
#   1. It must not expire. `sleep N` expired mid-session, the console stopped
#      accepting commands and it presented as a hung server. Hence `tail -f /dev/null`.
#   2. It must not share this shell's process group. On 2026-10-21 the non-expiring
#      holder died anyway -- it was signalled along with its launching shell -- and
#      the server then read EOF on stdin and shut down GRACEFULLY, KICKING THE
#      CONNECTED PLAYER. No `stop` was ever executed. Hence `setsid`.
#
# Property 1 alone was the 2026-10-21 fix and it was not enough; the second failure
# looked identical from the console side, which is why the symptom was misfiled as a
# hung console for a session. Both the holder AND the server run under `setsid`.
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

# Tear down remnants of a previous boot first: a surviving holder from a dead run
# keeps the old FIFO open and the new server inherits a console nobody writes to.
[ -f .server.pid ] && kill "$(cat .server.pid)" 2>/dev/null
[ -f .holder.pid ] && kill "$(cat .holder.pid)" 2>/dev/null
rm -f "$FIFO"
mkfifo "$FIFO"
# Non-expiring AND out of this shell's process group -- see the header.
# `setsid` without --fork execs in place when the caller is not a process-group
# leader, which is the case for a background job in a non-interactive script, so
# `$!` is still the real pid. Verified 2026-10-22: captured pid == the running
# process, `kill -0` succeeds, and its session id differs from this script's.
setsid nohup tail -f /dev/null > "$FIFO" 2>/dev/null &
HOLDER=$!
echo "$HOLDER" > .holder.pid

: > "$OUT"
setsid nohup "$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
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
# The holder's death is what ends a live session, so say whether it is alive rather
# than leaving it to be inferred from the server still being up.
kill -0 "$HOLDER" 2>/dev/null && echo "HOLDER alive (pid=$HOLDER)" || echo "HOLDER DEAD -- console is one write from EOF"
echo "SERVER_UP_VERDICT: READY | pid=$PID | connect a client to 127.0.0.1:5520"
