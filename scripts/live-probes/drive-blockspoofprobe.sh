#!/usr/bin/env bash
# HEADLESS run of /blockspoofprobe -- the server-side subset.
#
# Unlike the weather-override probe, this one answers real questions headless:
# tests 1-3 (host-block write, engine-native drop substitution with a SUBSTITUTED
# BlockType, and the host-type control) need no client. Expect PARTIAL -- tests 4
# and 5 skip, because a ground-item drop is anchored to an entity and a visual
# spoof needs eyes on a screen.
#
# Two outcomes are informative rather than failures:
#   NOT_RESIDENT  -- nothing is loaded at the headless probe position. Use the
#                    client driver instead; a connected player guarantees loaded
#                    chunks around themselves.
#   test 2 threw with a null breaker -- performBlockBreak may simply require a
#                    breaker entity. That is the exploratory case the probe labels
#                    as such; re-run with a client before concluding substitution
#                    does not work.
#
# Also runs /blockprobe first for contrast: it is a pure read that answers headless,
# so a working blockprobe beside a struggling blockspoofprobe separates "the block
# layer is fine" from "this probe's position is not loaded".
#
# The full answer needs drive-blockspoofprobe-client.sh.
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

out="blockspoofprobe-run.log"
fifo="$(mktemp -u /tmp/hyblockspoof.XXXXXX)"
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
echo "blockprobe" >&3
sleep 8
echo "blockspoofprobe" >&3
sleep 12
echo "stop" >&3
sleep 20
kill "$pid" 2>/dev/null
wait "$pid" 2>/dev/null
exec 3>&-
rm -f "$fifo"
echo "DRIVER_VERDICT: COMPLETE"
