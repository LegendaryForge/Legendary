#!/usr/bin/env bash
# Boots the test server headless, runs /anchordrainage, stops.
#
# Answers the risk the per-element topology decision names as the one to find before
# building: does a `Sprawling` current forest degenerate when the strength field is
# near-uniform? And measures, in the same walk, the nearest-stronger distance distribution
# that sets `reach`.
#
# Headless: terrain is read only through ChunkGenerator.generateHeight. EARTH ONLY --
# see mod/hytale/.../residue/GeneratorTerrainSample.java for why the other four are not
# measurable this way.
#
# THE DEGENERACY IS NOT A LONG CHAIN. A long chain is correct on a real gradient; that is
# what flowing uphill looks like. What is wrong is a chain following the position *hash*
# instead of the field, which happens wherever strengths tie. Both show up as depth, so
# depth cannot separate them -- the discriminator is edge-bearing coherence against a
# 12.5% chance line.
#
# Two controls bracket the question rather than validating the instrument:
#   flat -- every strength identical, so this IS the degenerate case; it shows how bad
#           bad looks, near the chance line
#   ramp -- a clean monotone gradient. NOT expected near 100%: every stronger anchor lies
#           east, so the nearest one spreads over the eastern half-plane, about four of
#           eight sectors. Predicted 30-60% before the run
# Real terrain is read between them.
#
# It also checks one claim that needs no measurement: at reach = suppressionRadius the
# edge count must be 0, because `beats` is a total order and the loser of any pair closer
# than the radius is not an anchor. A non-zero count there falsifies that argument.

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
OUT="anchordrainage-run.log"
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
echo "anchordrainage" >&3
FOUND=0
for _ in $(seq 1 1800); do
  if grep -aq "DRAINAGE_VERDICT:" "$OUT"; then FOUND=1; break; fi
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
  grep -a "DRAINAGE_VERDICT:" "$OUT" | tail -1
  echo "full output: $SERVER_DIR/$OUT"
  echo "DRIVER_VERDICT: COMPLETE"
else
  # An absent verdict is a failure, not a pass.
  echo "no DRAINAGE_VERDICT line in $SERVER_DIR/$OUT"
  echo "DRIVER_VERDICT: FAILED"
  exit 1
fi
