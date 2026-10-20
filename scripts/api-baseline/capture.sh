#!/usr/bin/env bash
# Capture a diffable snapshot of the Hytale server jar's API surface.
#
# WHY THIS EXISTS: on 2026-10-20 the launcher updated to 0.7.2 and overwrote the
# only copy of the previous jar on disk. The session that followed could say the
# build was red and could say what our code used, but could NOT say what the
# engine had changed -- there was nothing to diff against. This makes the next
# update a diff instead of an archaeology exercise.
#
# Run it AFTER a launcher update, commit the result, and diff against the
# previous snapshot directory.
set -uo pipefail
cd "$(dirname "$0")"
REPO="$(cd ../.. && pwd)"
OUT_ROOT="$(pwd)/snapshots"

# Same detection order as mod/hytale/build.gradle.kts, with an env override.
GAME="${HYTALE_HOME:-}"
if [ -z "$GAME" ]; then
  for c in "$HOME/.var/app/com.hypixel.HytaleLauncher/data/Hytale" "$HOME/.local/share/Hytale"; do
    [ -d "$c" ] && { GAME="$c"; break; }
  done
fi
PATCHLINE="${HYTALE_PATCHLINE:-release}"
JAR="$GAME/install/$PATCHLINE/package/game/latest/Server/HytaleServer.jar"

if [ ! -f "$JAR" ]; then
  echo "no server jar at $JAR"
  echo "BASELINE_VERDICT: FAILED"
  exit 2
fi

JAVAP="$(command -v javap || true)"
[ -z "$JAVAP" ] && for c in "$HOME"/.gradle/jdks/*/bin/javap; do [ -x "$c" ] && { JAVAP="$c"; break; }; done
if [ -z "$JAVAP" ]; then
  echo "no javap on PATH and none under ~/.gradle/jdks"
  echo "BASELINE_VERDICT: FAILED"
  exit 2
fi

VERSION="$(unzip -p "$JAR" META-INF/MANIFEST.MF | awk -F': ' '/^Implementation-Version/{print $2}' | tr -d '\r')"
REVID="$(unzip -p "$JAR" META-INF/MANIFEST.MF | awk -F': ' '/^Implementation-Revision-Id/{print $2}' | tr -d '\r')"
[ -z "$VERSION" ] && VERSION="unknown"
DIR="$OUT_ROOT/${VERSION}-${REVID:0:8}"
mkdir -p "$DIR"

# --- identity -------------------------------------------------------------
{
  unzip -p "$JAR" META-INF/MANIFEST.MF | grep -E '^(Implementation|Java-Version|Build-Jdk-Spec)' | tr -d '\r' | sort
  echo "Jar-SHA256: $(sha256sum "$JAR" | cut -d' ' -f1)"
  echo "Jar-Bytes: $(stat -c%s "$JAR")"
} > "$DIR/identity.txt"

# --- top-level class inventory -------------------------------------------
# Top-level only: inner-class churn is noise, and additions/removals/moves of
# top-level types is the signal. LIMIT: a change confined to an inner class is
# invisible here -- signatures.txt below covers that for types we actually use.
unzip -Z1 "$JAR" 'com/hypixel/*' \
  | grep '\.class$' | grep -v '\$' \
  | sed 's|\.class$||;s|/|.|g' | LC_ALL=C sort > "$DIR/classes.txt"

# --- signatures for the exact types this project imports ------------------
# Derived from sources every run, so it tracks our dependency surface rather
# than a hand-maintained list that would drift.
mapfile -t TYPES < <(grep -rhoE '^import (static )?com\.hypixel\.[A-Za-z0-9_.]+' "$REPO/mod/hytale/src/main/java/" \
  | sed 's/^import \(static \)\?//;s/;$//' | LC_ALL=C sort -u)

: > "$DIR/signatures.txt"
MISSING=0
for t in "${TYPES[@]}"; do
  if out="$("$JAVAP" -cp "$JAR" "$t" 2>/dev/null)" && [ -n "$out" ]; then
    printf '===== %s\n%s\n\n' "$t" "$out" >> "$DIR/signatures.txt"
  else
    # Recorded, never silently dropped: an absent type is the most important
    # thing a baseline can tell you, and suppressing it would fail green.
    printf '===== %s\nUNRESOLVED (not a top-level class, or absent from this jar)\n\n' "$t" >> "$DIR/signatures.txt"
    MISSING=$((MISSING + 1))
  fi
done

echo "snapshot:   $DIR"
echo "version:    $VERSION  rev ${REVID:0:8}"
echo "classes:    $(wc -l < "$DIR/classes.txt") top-level com.hypixel types"
echo "signatures: ${#TYPES[@]} imported types, $MISSING unresolved"
echo "BASELINE_VERDICT: CAPTURED"
