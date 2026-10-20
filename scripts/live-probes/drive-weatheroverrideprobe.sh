#!/usr/bin/env bash
# One boot cycle. Runs /weatheroverrideprobe from the console with no client
# attached, which exercises registration and the no-player path.
#
# The probe's real answers need a CONNECTED CLIENT: the subject of an override is
# a player's WeatherTracker and the observable is a packet to that player's
# connection, so a headless run is no run. It must report SKIPPED here -- if it
# reports PASS with zero players, the probe is lying and that is the bug to fix.
#
# Also runs /weatherprobe for contrast: that one is a pure read and DOES answer
# headless, so a green weatherprobe beside a skipped weatheroverrideprobe shows
# the skip is about players rather than about weather being unavailable.
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

out="weatheroverrideprobe-run.log"
fifo="$(mktemp -u /tmp/hyweather.XXXXXX)"
mkfifo "$fifo"
: > "$out"
"$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
  --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$fifo" > "$out" 2>&1 &
pid=$!
exec 3>"$fifo"
ready=no
for i in $(seq 1 300); do
  grep -q "Universe ready!" "$out" && { ready=yes; break; }
  kill -0 "$pid" 2>/dev/null || { echo "  SERVER DIED EARLY"; break; }
  sleep 1
done
echo "  READY=$ready after ${i}s"
sleep 5
echo "weatherprobe" >&3
sleep 8
echo "weatheroverrideprobe" >&3
sleep 12
echo "stop" >&3
sleep 20
kill "$pid" 2>/dev/null
wait "$pid" 2>/dev/null
exec 3>&-
rm -f "$fifo"
echo "DRIVER_VERDICT: COMPLETE"
