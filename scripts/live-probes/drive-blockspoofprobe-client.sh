#!/usr/bin/env bash
# CLIENT-ATTACHED run of /blockspoofprobe.
#
# Unlike the other drivers this one does NOT stop promptly after the probe. Test 5
# of the probe cannot be measured from the server -- whether a client renders a
# spoofed block, and how long the illusion survives, is visible only to the person
# looking at it. So the driver runs the probe and then HOLDS THE SERVER OPEN while
# you do the five observation steps it prints, then stops on its own.
#
# Hold duration in seconds, default 900 (15 min):
#   ./drive-blockspoofprobe-client.sh 1800
set -uo pipefail

# A write to the console FIFO after the server has exited raises SIGPIPE, which
# terminates the shell outright -- so the driver died before printing either verdict
# line. An absent verdict is a FAILURE, not a pass, and a driver that goes silent
# exactly when the server dies early cannot report the case you most need reported.
# Ignoring PIPE turns that into an ordinary failed write, which send_console handles.
trap '' PIPE

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
HOLD_SECONDS="${1:-900}"
PROBE_TIME="${HYTALE_PROBE_TIME-midday}"

if ss -lun 2>/dev/null | grep -q ':5520'; then
  echo "  !! Something is already listening on 5520 -- probably a server you started by hand."
  ss -lunp 2>/dev/null | grep ':5520' | sed 's/^/  !! /'
  echo "  !! Type 'stop' in that server's window, then re-run this."
  echo "DRIVER_VERDICT: ABORTED (port 5520 already in use)"
  exit 1
fi

out="blockspoofprobe-client-run.log"
fifo="$(mktemp -u /tmp/hyblockspoof.XXXXXX)"
mkfifo "$fifo"
: > "$out"
"$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
  --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$fifo" > "$out" 2>&1 &
pid=$!
exec 3>"$fifo"

cleanup() { exec 3>&- 2>/dev/null; kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; rm -f "$fifo"; }

# Every console write goes through here. Returns non-zero rather than killing the
# driver when the server is already gone, so the verdict lines below always print.
send_console() {
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "  (server already exited -- '$1' not sent)"
    return 1
  fi
  echo "$1" >&3 2>/dev/null || { echo "  (console write failed -- server gone)"; return 1; }
}

auth_remedy() {
  echo ""
  echo "  !! THE SERVER CANNOT AUTHENTICATE ITSELF -- no client can join, whatever it does."
  echo "  !! auth.enc's refresh token has expired. The port binds and your identity token"
  echo "  !! validates; the refusal is the server's, and it reads as a plain connection failure."
  grep -ho 'The refresh token expired[^"]*' "$out" | head -1 | sed 's/^/  !! server said: /'
  echo "  !! Remedy: bash $SERVER_DIR/run-server.sh  then  /auth login device  (approve in browser)"
}

ready=no
for i in $(seq 1 300); do
  grep -q "Universe ready!" "$out" && { ready=yes; break; }
  kill -0 "$pid" 2>/dev/null || { echo "  SERVER DIED EARLY"; break; }
  sleep 1
done
echo "  READY=$ready after ${i}s"
if [ "$ready" != yes ]; then
  cleanup; echo "DRIVER_VERDICT: FAILED (server never became ready)"; exit 1
fi

if grep -q -e 'Failed to restore session from stored credentials' \
           -e 'No valid OAuth tokens to create game session' "$out"; then
  auth_remedy
  send_console stop; sleep 10; cleanup
  echo "DRIVER_VERDICT: ABORTED (server auth unavailable -- client cannot join)"
  exit 1
fi

echo ""
echo "  >>> CONNECT THE CLIENT NOW: Direct Connect 127.0.0.1:5520"
echo "  >>> waiting up to 300s for a player to join..."
joined=no
refused=no
for i in $(seq 1 300); do
  grep -q "Player joined:" "$out" && { joined=yes; break; }
  grep -q "serverAuthUnavailable" "$out" && { refused=yes; break; }
  kill -0 "$pid" 2>/dev/null || { echo "  SERVER DIED WHILE WAITING"; break; }
  sleep 1
done
echo "  JOINED=$joined after ${i}s"

if [ "$refused" = yes ]; then
  echo "  A client reached the server and was REFUSED at login."
  auth_remedy
  send_console stop; sleep 10; cleanup
  echo "DRIVER_VERDICT: ABORTED (client refused: serverAuthUnavailable)"
  exit 1
fi

if [ "$joined" != yes ]; then
  echo "  No player joined. Tests 4 and 5 would report SKIPPED and test 5 is the whole point,"
  echo "  so the probe was not run. Use drive-blockspoofprobe.sh for the headless subset."
  send_console stop; sleep 15; cleanup
  echo "DRIVER_VERDICT: ABORTED (no client)"
  exit 1
fi

# Daylight BEFORE the spoof, never after. Test 5 is a visual comparison of three
# blocks, and `time set` re-rolls every environment's forecast -- which can trigger a
# client relight and clear the illusion. On 2026-10-20 this was sent AFTER the probe:
# the reading became ambiguous between "spoofing broke" and "the time change broke it",
# and the run had to be re-fired. Ordering it here makes the lighting a reproducible
# precondition rather than an ad-hoc console write. Set HYTALE_PROBE_TIME= (empty) to
# leave the world clock alone, or to dusk/midnight/<hour> for a different condition.
if [ -n "$PROBE_TIME" ]; then
  send_console "time set $PROBE_TIME"
  sleep 3
fi

# Let the join settle so the player's own chunks are resident -- the probe writes into
# the section at the player's position and reports NOT_RESIDENT rather than guessing.
sleep 6
send_console blockspoofprobe
sleep 12

echo ""
echo "--- probe output ---"
sed -n '/per-player block spoof/,/BLOCK_SPOOF_VERDICT/p' "$out" | sed 's/\x1b\[[0-9;]*m//g'
echo ""
echo "============================================================"
echo "  SERVER HELD OPEN FOR ${HOLD_SECONDS}s -- do the test-5 steps above now."
echo "  Nothing is being measured during this window; the probe already printed"
echo "  everything it can see. What matters is what YOU see."
echo "  If the illusion is lost and you want it back, re-run this driver -- this"
echo "  console is script-driven, so you cannot type into it yourself."
echo "============================================================"
held_full=yes
for ((t=HOLD_SECONDS; t>0; t-=60)); do
  sleep $(( t < 60 ? t : 60 ))
  kill -0 "$pid" 2>/dev/null || { echo "  server exited during hold"; held_full=no; break; }
  [ $((t-60)) -gt 0 ] && echo "  ... $((t-60))s left"
done

send_console stop
sleep 20
cleanup

echo ""
grep -o 'BLOCK_SPOOF_VERDICT: [A-Z]*' "$out" | tail -1 \
  || echo "BLOCK_SPOOF_VERDICT: ABSENT (probe never printed -- that is a failure, not a pass)"
if [ "$held_full" = yes ]; then
  echo "DRIVER_VERDICT: COMPLETE"
else
  echo "DRIVER_VERDICT: COMPLETE (hold ended early -- server exited before the window closed)"
fi
