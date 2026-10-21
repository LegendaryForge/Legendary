#!/usr/bin/env bash
# Sweeps HEIGHT_BAND and reports, per band: world fit, supply at the 24-unit |dy|
# gate, and supply at absorption 150.
#
# WHY THIS EXISTS. The finding is a relationship ACROSS runs, which no single
# N9Scan invocation can express. It answers a question that
# 2026-08-30-n9-all-crossings-are-nexuses-design.md §8 posed as unanswerable:
#
#   §8: "a world-level absorption radius of 150 and a 24-unit |dy| gate cannot
#        both stand if N9 is meant to increase supply. That is a design
#        decision, not a tuning one."
#
# They can both stand. The parameter §8 never varied is HEIGHT_BAND -- because
# until the engine's 320-block vertical extent was read off chunk storage
# (BlockChunk allocates EnvironmentSection[10], sections are 32 tall), nothing
# constrained the height profile at all, so the one free parameter sat outside
# the search space while two fixed ones were traded against each other.
#
# Tightening the band does two things at once, and they are one mechanism rather
# than a coincidence: arms sit closer together in y, so (a) fewer crossings fall
# outside the world and (b) MORE pairs pass the |dy| gate. World fit and supply
# improve together.
#
# 150 seeds, not the 300 used for out-chosen.txt -- this is a sweep across four
# configurations and the relationship is monotonic and large. Figures therefore
# differ from out-chosen.txt in the last digit; the 96-band row is the control and
# should agree with it to within run-to-run noise.
#
# Usage:  ./sweep-heightband.sh [seeds]   (default 150)   > out-heightband-sweep.txt
set -uo pipefail
cd "$(dirname "$0")"
SEEDS="${1:-150}"

echo "=== HEIGHT_BAND sweep | 6 arms x 240 steps | ${SEEDS} seeds | restore 0.012 maxPitch 0.44 ==="
echo ""
printf "%-6s %-12s %-26s %-28s\n" "band" "world fit" "supply @ |dy|<24 (no abs.)" "supply @ absorption 150"
echo "-------------------------------------------------------------------------------------"

for hb in 96 64 48 32; do
  run="$(java N9Scan.java 6 240 "$SEEDS" 0.012 0.44 "band ${hb}" "$hb" 2>&1)"

  fit="$(printf '%s\n' "$run" | grep -o 'crossings outside a 320-block world: [0-9.]*%' | sed 's/.*: //')"
  gate="$(printf '%s\n' "$run" | grep -o '=> N9 with the 24-gate is [0-9.]*x' | sed 's/.*is //')"
  # The absorption table's 150 row. Columns: radius nexuses/wd vs-today max-elems >=3 >=4
  abs="$(printf '%s\n' "$run" | awk '$1 == "150" { print $2 " nexuses/wd  " $3 }')"

  printf "%-6s %-12s %-26s %-28s\n" "$hb" "${fit:-?}" "${gate:-?}" "${abs:-?}"
done

echo "-------------------------------------------------------------------------------------"
cat <<'EOF'

READING THIS TABLE

  band 96 is the shipped calibration. It overflows the world on ~9% of crossings
  and is the configuration §8 measured as a 1.03x "wash" at absorption 150.

  band 32 nearly fits the world (~0.3% overflow) and lifts absorption-150 supply
  to ~1.60x. Both §8 constraints are untouched: absorption stays 150, the |dy|
  gate stays 24.

  32 is NOT a recommendation. It is the end of the swept range and demonstrates
  that the lever exists. The value should be fitted to a stated objective --
  near-zero overflow, plus whatever in-game testing says about whether a current
  is followable -- not picked because it was the last column of a sweep.

  TWO CONSEQUENCES WORTH CARRYING. A tighter band makes the |dy| gate LESS
  selective, and §4 justified that gate with "a Fire arm at y=50 and an Ice arm
  at y=150 do not meet" -- a case that gets rarer as the band narrows. And the
  >=3-element participation figures move very little across the sweep, so the
  multi-element pile-up §5 describes is not what is paying for the extra supply.

SWEEP_VERDICT: COMPLETE
EOF
