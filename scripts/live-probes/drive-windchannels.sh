#!/usr/bin/env bash
# Boots the test server headless, runs /windchannels, stops.
#
# Headless by design. /windchannels reads terrain only through
# ChunkGenerator.generateHeight, a pure generator call that answers at any coordinate with
# nothing loaded, so this probe needs no client and loads no chunks. See
# mod/hytale/.../residue/GeneratorTerrainSample.java.
#
# What it answers: does this generator produce laterally constricted channels -- ravines,
# gorges, saddles -- in enough quantity and with enough spatial coherence to carry a wind
# network? That is the fact the Wind grounding proposal is contingent on, and nobody had
# looked.
#
# IT CANNOT SEE CAVES OR TUNNELS, and that is structural rather than a gap: the
# generator-backed sample answers solidity as `y <= height`, so a hole under the surface is
# rock by construction. A low channel count here is evidence about surface terrain only.
#
# Three controls run BEFORE the real terrain, negatives first:
#   flat   -- no relief at all          -> must be 100% OPEN
#   slope  -- relief, no constriction   -> must also be 100% OPEN
#   trench -- a gorge drawn on purpose  -> must match its predicted channel count exactly
# The positive control is the load-bearing one. A near-zero result on real terrain is
# ambiguous between "the terrain is smooth" and "the detector does not work", and the
# standing rule here is that a negative among positives is ambiguous while a negative on
# its own is not.
#
# The probe wait is deliberately long: the sweep classifies ~263k columns at five ring
# distances and four rise thresholds, and its duration has never been measured. The probe
# prints its own fetch and sweep timings so the next run can be sized from data.

set -uo pipefail
SERVER_DIR="${HYTALE_TEST_SERVER:-$(cd "$(dirname "$0")/../../.scratch/hytale-server" && pwd)}"
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [ ! -f "$SERVER_DIR/run-server.sh" ]; then
  echo "No test-server harness at $SERVER_DIR -- see scripts/live-probes/README.md" >&2
  echo "DRIVER_VERDICT: FAILED"
  exit 1
fi
cd "$SERVER_DIR"
GAME="$HOME/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest"
JAVA="$HOME/.gradle/jdks/jetbrains_s_r_o_-25-amd64-linux.2/bin/java"
OUT="windchannels-run.log"
FIFO="$(mktemp -u /tmp/hyprobe.XXXXXX)"
mkfifo "$FIFO"

JAR="$(ls -1t "$REPO_ROOT"/mod/hytale/build/libs/*-all.jar 2>&1 | head -1)"
if [ ! -f "$JAR" ]; then
  echo "No shadow jar; run ./gradlew :mod:hytale:shadowJar first" >&2
  echo "DRIVER_VERDICT: FAILED"
  exit 1
fi
mkdir -p mods
# The deploy filename MUST match server-up.sh's exactly, or mods/ holds two copies of the
# same plugin and the server refuses to boot on "Tried to load duplicate plugin".
DEPLOYED="mods/hytale-0.0.0-SNAPSHOT-all.jar"
cp "$JAR" "mods/.deploy.tmp" && mv -f "mods/.deploy.tmp" "$DEPLOYED"
echo "MOD DEPLOYED $(stat -c%s "$DEPLOYED") bytes"
STRAY="$(find mods -maxdepth 1 -name '*.jar' ! -name "$(basename "$DEPLOYED")" -printf '%f\n')"
if [ -n "$STRAY" ]; then
  echo "STRAY PLUGIN JAR(S) in mods/, which boots as a duplicate-plugin shutdown:" >&2
  echo "$STRAY" >&2
  echo "DRIVER_VERDICT: FAILED"
  exit 1
fi

: > "$OUT"
"$JAVA" -jar "$GAME/Server/HytaleServer.jar" \
  --assets "$GAME/Assets.zip" --disable-sentry --allow-op < "$FIFO" > "$OUT" 2>&1 &
PID=$!
exec 3>"$FIFO"

for _ in $(seq 1 180); do
  grep -aq "Universe ready!" "$OUT" && break
  kill -0 "$PID" 2>/dev/null || { echo "SERVER DIED EARLY"; break; }
  sleep 1
done
sleep 5

# Waits for the verdict line by name. `$OUT` is freshly truncated above and this server is
# the only writer, so a plain grep here is sound -- unlike live-session.log, which
# interleaves two boots' output.
echo "windchannels" >&3
FOUND=0
for _ in $(seq 1 600); do
  if grep -aq "WINDCHANNEL_VERDICT:" "$OUT"; then FOUND=1; break; fi
  kill -0 "$PID" 2>/dev/null || { echo "SERVER DIED DURING PROBE"; break; }
  sleep 1
done

echo "stop" >&3
sleep 10
kill "$PID" 2>/dev/null
wait "$PID" 2>/dev/null
exec 3>&-
rm -f "$FIFO"

if [ "$FOUND" -eq 1 ]; then
  grep -a "WINDCHANNEL_VERDICT:" "$OUT" | tail -1
  echo "full output: $SERVER_DIR/$OUT"
  echo "DRIVER_VERDICT: COMPLETE"
else
  # An absent verdict is a failure, not a pass.
  echo "no WINDCHANNEL_VERDICT line in $SERVER_DIR/$OUT"
  echo "DRIVER_VERDICT: FAILED"
  exit 1
fi
