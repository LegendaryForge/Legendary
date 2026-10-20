#!/usr/bin/env bash
# CLIENT-ATTACHED run of /weatheroverrideprobe. This is the one that produces the
# real answers -- the headless driver beside it can only confirm the honest skip.
#
# Boots the server, then WAITS for a player to join and fires the probe itself, so
# there is no window to mistime. Connect the game client via Direct Connect to
# 127.0.0.1:5520 when prompted.
#
# The probe is fired from the CONSOLE, not from chat, deliberately: its output is
# ~60 lines and context.sendMessage goes to whoever issued it. In the chat box that
# is unreadable; on the console it lands in this log.
#
# Pass a weather id as $1 to cover test 10 (precedence over a world force) -- the
# probe refuses to force weather itself because forcing starves the
# environmentWeather refresh, which compareAndSwapHour then gates for up to an
# in-game hour. Doing it from here makes the side effect the operator's explicit
# choice. Example:  ./drive-weatheroverrideprobe-client.sh Zone1_Rain
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
FORCE_WEATHER="${1:-}"

# A server already on 5520 is almost always the interactive one used to run
# `/auth login device`. This driver must own the boot, because it needs FIFO
# console control to fire the probe -- so say that rather than letting the second
# boot fail on bind, which reads like an unrelated transport error.
if ss -lun 2>/dev/null | grep -q ':5520'; then
  echo "  !! Something is already listening on 5520 -- probably a server you started by hand."
  ss -lunp 2>/dev/null | grep ':5520' | sed 's/^/  !! /'
  echo "  !! Type 'stop' in that server's window (or kill the pid above), then re-run this."
  echo "  !! This driver needs FIFO console control to fire the probe, so it must boot its own."
  echo "DRIVER_VERDICT: ABORTED (port 5520 already in use)"
  exit 1
fi

out="weatheroverrideprobe-client-run.log"
fifo="$(mktemp -u /tmp/hyweatherc.XXXXXX)"
mkfifo "$fifo"
: > "$out"
"$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
  --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$fifo" > "$out" 2>&1 &
pid=$!
exec 3>"$fifo"

cleanup() { exec 3>&- 2>/dev/null; kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; rm -f "$fifo"; }

auth_remedy() {
  echo ""
  echo "  !! THE SERVER CANNOT AUTHENTICATE ITSELF -- no client can join, whatever it does."
  echo "  !! The server authenticates against Hytale's OAuth service on its own behalf, and"
  echo "  !! auth.enc's refresh token has expired. Everything else looks healthy: the port"
  echo "  !! binds, the QUIC connection is accepted, and YOUR identity token validates. The"
  echo "  !! refusal is the server's, not yours, and it reads as a plain connection failure."
  grep -ho 'The refresh token expired[^"]*' "$out" | head -1 | sed 's/^/  !! server said: /'
  echo "  !! Remedy -- run the server interactively and refresh its credentials:"
  echo "  !!     bash $(pwd)/run-server.sh"
  echo "  !!     /auth login device     (approve the printed code in a browser)"
  echo "  !!     /auth status           (expect an authenticated session)"
  echo "  !!     stop"
  echo "  !! That rewrites auth.enc, so it persists until the next expiry. Then re-run this."
}

ready=no
for i in $(seq 1 300); do
  grep -q "Universe ready!" "$out" && { ready=yes; break; }
  kill -0 "$pid" 2>/dev/null || { echo "  SERVER DIED EARLY"; break; }
  sleep 1
done
echo "  READY=$ready after ${i}s"
if [ "$ready" != yes ]; then
  cleanup
  echo "DRIVER_VERDICT: FAILED (server never became ready)"
  exit 1
fi

# Pre-flight on server-side auth, keyed on the restore-failure lines. This is the
# cheap check; the authoritative one is the serverAuthUnavailable break below,
# which keys on an actual refusal rather than on a predicted cause.
if grep -q -e 'Failed to restore session from stored credentials' \
           -e 'No valid OAuth tokens to create game session' "$out"; then
  auth_remedy
  echo "stop" >&3
  sleep 10
  cleanup
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
  # A refusal is proof the wait is pointless. Without this the loop cannot tell
  # "operator has not connected yet" from "no client can ever connect", and spends
  # its full 300s either way -- which is exactly what happened on 2026-10-20.
  grep -q "serverAuthUnavailable" "$out" && { refused=yes; break; }
  kill -0 "$pid" 2>/dev/null || { echo "  SERVER DIED WHILE WAITING"; break; }
  sleep 1
done
echo "  JOINED=$joined after ${i}s"

if [ "$refused" = yes ]; then
  echo "  A client reached the server and was REFUSED at login."
  auth_remedy
  echo "stop" >&3
  sleep 10
  cleanup
  echo "DRIVER_VERDICT: ABORTED (client refused: serverAuthUnavailable)"
  exit 1
fi

if [ "$joined" = yes ]; then
  # Let the join settle: the weather tracker needs one real tick before the probe
  # reads a baseline, and a baseline of 0 would make the no-op dedupe ambiguous.
  sleep 5
  if [ -n "$FORCE_WEATHER" ]; then
    echo "  forcing world weather to '$FORCE_WEATHER' to cover test 10"
    echo "weather set $FORCE_WEATHER" >&3
    sleep 3
  fi
  echo "weatherprobe" >&3
  sleep 6
  echo "weatheroverrideprobe" >&3
  sleep 15
  if [ -n "$FORCE_WEATHER" ]; then
    echo "  clearing the world force"
    echo "weather reset" >&3
    sleep 3
  fi
else
  echo "  no player joined -- the probe would only report SKIPPED, so it was not run"
fi

echo "stop" >&3
sleep 20
cleanup

echo ""
echo "--- probe output ---"
sed -n '/per-player weather override probe/,/WEATHER_OVERRIDE_VERDICT/p' "$out" | sed 's/\x1b\[[0-9;]*m//g'
echo ""
grep -o 'WEATHER_OVERRIDE_VERDICT: [A-Z]*' "$out" | tail -1 \
  || echo "WEATHER_OVERRIDE_VERDICT: ABSENT (probe never printed -- that is a failure, not a pass)"
echo "DRIVER_VERDICT: COMPLETE"
