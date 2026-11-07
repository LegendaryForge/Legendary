#!/usr/bin/env bash
# Boots the test server headless, runs /residueseparation, stops.
#
# Headless by design, and that is the whole point of this probe.
#
# /residueseparation measures whether narrowing the measurement window makes anchor
# selection less arbitrary. The question it serves was left open as "Unknown until walked
# to" -- and walking to it needs a client. This does not, because it reads terrain through
# ChunkGenerator.generateHeight, a pure generator call that answers at any coordinate with
# nothing loaded. See mod/hytale/.../residue/GeneratorTerrainSample.java.
#
# EARTH ONLY. Wind, Water and Fire need fluid or environment data that a generator-backed
# sample does not have, and Lightning has no measure by any route. The adapter's javadoc
# states why for each, and the probe prints the restriction in its own output.
#
# It runs a FLAT-TERRAIN CONTROL first. On flat ground every contest must be an exact tie;
# if it is not, the instrument is measuring something other than terrain and the probe says
# the real rows are not evidence.

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
OUT="residueseparation-run.log"
FIFO="$(mktemp -u /tmp/hyprobe.XXXXXX)"
mkfifo "$FIFO"

# Deploy the jar rather than trusting whatever is in mods/ -- same reason server-up.sh does.
JAR="$(ls -1t "$REPO_ROOT"/mod/hytale/build/libs/*-all.jar 2>&1 | head -1)"
if [ ! -f "$JAR" ]; then
  echo "No shadow jar; run ./gradlew :mod:hytale:shadowJar first" >&2
  echo "DRIVER_VERDICT: FAILED"
  exit 1
fi
mkdir -p mods
# The deploy filename MUST match server-up.sh's exactly. This script originally wrote
# mods/hytale-all.jar while server-up.sh writes mods/hytale-0.0.0-SNAPSHOT-all.jar, so after
# using both the directory held two copies of the same plugin and the server refused to boot:
#   SEVERE [PluginManager] Tried to load duplicate plugin: io.github.legendaryforge:LegendaryHytale
#   [HytaleServer] Shutting down... 'client.disconnection.shutdownReason.pluginDuplicate'
# The driver reported only "SERVER DIED EARLY", which named the symptom and not the cause.
DEPLOYED="mods/hytale-0.0.0-SNAPSHOT-all.jar"
cp "$JAR" "mods/.deploy.tmp" && mv -f "mods/.deploy.tmp" "$DEPLOYED"
echo "MOD DEPLOYED $(stat -c%s "$DEPLOYED") bytes"
# Fail loudly on any OTHER copy of our plugin rather than letting the server die at boot.
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

# The verdict line is what this waits for, by name. Not a line count, and not a fixed sleep:
# Part 1b walks 1,429 entries on a worldgen thread and its duration is not predictable.
# `$OUT` is freshly truncated above and this server is the only writer, so counting
# occurrences here is sound -- unlike live-session.log, which interleaves two boots.
echo "residueseparation" >&3
FOUND=0
for _ in $(seq 1 120); do
  if grep -aq "SEPARATION_VERDICT:" "$OUT"; then FOUND=1; break; fi
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
  grep -a "SEPARATION_VERDICT:" "$OUT" | tail -1
  echo "full output: $SERVER_DIR/$OUT"
  echo "DRIVER_VERDICT: COMPLETE"
else
  # An absent verdict is a failure, not a pass -- the 2026-08-11 red gate was caught
  # by its absence from a log and nothing else.
  echo "no SEPARATION_VERDICT line in $SERVER_DIR/$OUT"
  echo "DRIVER_VERDICT: FAILED"
  exit 1
fi
