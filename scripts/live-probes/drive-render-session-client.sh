#!/usr/bin/env bash
# CLIENT-ATTACHED render session: /beamprobe (rank 3) then /markerprobe (rank 4).
#
# WHY TWO PROBES IN ONE DRIVER. Every other driver here is one-probe-one-script, and
# that is the right default. These two are paired because the README's own taxonomy
# puts them in the same class -- "anything whose observable is rendering" -- and the
# scarce resource for that class is not the server, it is a human sitting at a
# connected client. Both probes have exactly one remaining question and it is the
# same question for both:
#
#   rank 3  /beamprobe   -- the server reports a VALID holder. Does a beam DRAW?
#                           (capability assessment §8 open question 3)
#                        -- and does BeamComponent.spawn still return null in a
#                           force-loaded chunk when a client is attached, or was
#                           that a headless artifact? (§8 open question 2)
#   rank 4  /markerprobe -- the discovery gate is server-readable, but does the
#                           marker APPEAR on the player's map once revealed? (§5.4)
#
# Splitting them would make the operator connect twice to answer two halves of one
# question. So: one boot, one join, one hold window, both probes.
#
# THE HOLD IS THE POINT. Like drive-blockspoofprobe-client.sh, this does not
# fire-and-stop. After the probes run it holds the server open (default 900s,
# override as $1) so the beams and the map marker can actually be looked at.
# NOTHING IS MEASURED DURING THE HOLD -- its silence is not a result.
#
# /markerprobe is fired TWICE on purpose. WorldMapManager is a TickingThread, so a
# registered MarkerProvider fires asynchronously and the first invocation cannot
# observe its own publication: getSentMarkers() will normally not yet contain the
# marker. The second invocation is the read-back. An absence on run 1 is expected;
# an absence on run 2 is a finding.
#
# Probes fire from the CONSOLE, not chat: output runs to ~60 lines each and
# context.sendMessage goes to whoever issued the command, which in a chat box is
# unreadable. On the console it lands in this log.
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
HOLD="${1:-900}"

# A server already on 5520 is almost always the interactive one used to run
# `/auth login device`. This driver must own the boot, because it needs FIFO
# console control to fire the probes -- so say that rather than letting the second
# boot fail on bind, which reads like an unrelated transport error.
if ss -lun 2>/dev/null | grep -q ':5520'; then
  echo "  !! Something is already listening on 5520 -- probably a server you started by hand."
  ss -lunp 2>/dev/null | grep ':5520' | sed 's/^/  !! /'
  echo "  !! Type 'stop' in that server's window (or kill the pid above), then re-run this."
  echo "  !! This driver needs FIFO console control to fire the probes, so it must boot its own."
  echo "DRIVER_VERDICT: ABORTED (port 5520 already in use)"
  exit 1
fi

out="render-session-client-run.log"
fifo="$(mktemp -u /tmp/hyrender.XXXXXX)"
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

# Pre-flight on server-side auth, keyed on the restore-failure lines. Cheap check;
# the authoritative one is the serverAuthUnavailable break in the join loop below,
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
  # its full 300s either way.
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

if [ "$joined" != yes ]; then
  echo "  no player joined -- both probes would only report SKIPPED, so neither was run"
  echo "stop" >&3
  sleep 20
  cleanup
  echo "DRIVER_VERDICT: ABORTED (no player joined)"
  exit 1
fi

# Let the join settle. Chunk tracking is driven by connected players, and §8's open
# question 2 is precisely whether entity spawning behaves differently once that
# tracking exists -- so firing before it settles would reproduce the headless
# result and prove nothing.
sleep 8

# ORDER MATTERS, AND THE FIRST VERSION GOT IT WRONG.
#
# /beamprobe spawns its holders with a 60s lifetime so it cleans up after itself.
# The original ordering fired it FIRST, then ran both marker probes (~51s of
# sequence), so the beams expired within seconds of the hold beginning -- i.e. the
# 15-minute window that exists purely so a human can look at them did not overlap
# their lifetime at all. On the first live run (2026-10-20) they were gone before
# the operator could check, and the render answer was recovered only because they
# had glanced east earlier by chance.
#
# So the marker probes go first: markers are PERSISTENT state on the player, so
# nothing about them decays while the beams are being looked at. /beamprobe fires
# LAST, immediately before the hold, giving its 60s the best overlap available.
#
# A 60s lifetime is still shorter than the hold. That is deliberate rather than
# unfixed: raising it would leave holder entities in the world for the operator to
# clean up, and the probe's self-cleaning property is worth more than a longer
# look. Re-issue `beamprobe` on the console during the hold for another 60s.

echo "  --- rank 4: /markerprobe (run 1 of 2 -- registers the provider) ---"
echo "markerprobe" >&3
sleep 12

# The read-back. The provider needs at least one map tick to publish. Measured:
# run 1 reports contains=false, run 2 reports contains=true.
echo "  --- rank 4: /markerprobe (run 2 of 2 -- publication read-back) ---"
sleep 15
echo "markerprobe" >&3
sleep 12

echo ""
echo "  --- rank 3: /beamprobe (client attached, fired last -- 60s lifetime) ---"
echo "beamprobe" >&3
sleep 12

cat <<EOF

  ============================================================
  HOLDING THE SERVER OPEN FOR ${HOLD}s -- NOTHING IS MEASURED NOW.
  Everything below is a question only you can answer.

  rank 3 -- beams.  LOOK NOW: 60s LIFETIME, fired just before this.
    [ ] Do any beams DRAW?  Look ~2 blocks +x of where you stood and
        1 block up, spread about 8 blocks across z, running ~10 blocks
        further east.
    [ ] The probe prints a holder VALID/null per test. A VALID holder
        with nothing drawn is a DIFFERENT failure from a null holder --
        report which one you see, per test name.
    [ ] Expired already?  Type \`beamprobe\` on the console for another 60s.

    ANSWERED 2026-10-20 (both, so these are regression checks now):
      q2 -- spawn returning null in a force-loaded chunk was a HEADLESS
            ARTIFACT. Client attached: 5 VALID / 0 null.
      q3 -- beams DO render. The 'Basic' asset draws as a chain of blue
            X billboards forming a line, not a smooth beam.

  rank 4 -- map marker (§5.4).  No hurry: persistent player state.
    [ ] Open the world map. Is there a Campfire icon about 24 blocks
        +x of where you stood, named "Legendary marker probe"?
    [ ] It is deliberately a Campfire and deliberately NOT anything
        residue-shaped, so it cannot be confused with a real feature.
    [ ] If the server said revealed=true and no icon appears, that is
        the finding: the gate works and publication does not.

    ANSWERED 2026-10-20: the marker renders, correctly named.

  STILL OPEN, and this probe cannot answer it: /beamprobe renders only
  the FIRST asset that resolves, which is 'Basic'. Chain, Fire and Rope
  have never been seen. Which of the four reads as a residue CURRENT is
  an aesthetic question needing all four drawn side by side.

  Type 'stop' is NOT needed -- this exits on its own.
  ============================================================

EOF
sleep "$HOLD"

echo "stop" >&3
sleep 20
cleanup

echo ""
echo "--- /beamprobe output ---"
sed -n '/beam probe/,/BEAM_PROBE_VERDICT/p' "$out" | sed 's/\x1b\[[0-9;]*m//g'
echo ""
echo "--- /markerprobe output (both runs) ---"
sed -n '/world map marker probe/,/MARKER_PROBE_VERDICT/p' "$out" | sed 's/\x1b\[[0-9;]*m//g'
echo ""
# Read verdicts BY NAME, never by position -- five recorded occasions in this
# workspace where something appended output after a verdict and the reader saw the
# wrapper. An ABSENT verdict is a failure, not a pass.
grep -o 'BEAM_PROBE_VERDICT: [A-Z]*' "$out" | tail -1 \
  || echo "BEAM_PROBE_VERDICT: ABSENT (probe never printed -- that is a failure, not a pass)"
grep -o 'MARKER_PROBE_VERDICT: [A-Z]*' "$out" | tail -1 \
  || echo "MARKER_PROBE_VERDICT: ABSENT (probe never printed -- that is a failure, not a pass)"
echo "DRIVER_VERDICT: COMPLETE"
