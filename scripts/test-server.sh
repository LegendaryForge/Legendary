#!/usr/bin/env bash
#
# Drive the reusable Hytale test server.
#
# This script exists to make three recorded traps unreachable rather than merely documented.
# See docs/integration/live-server-testing.md for the full set.
#
#   1. The console needs a REAL TTY. With plain redirected stdin, JLine falls back to a `dumb`
#      terminal and silently ignores every typed command — no error, no echo. We wrap the boot in
#      `script -qfc`.
#   2. The FIFO holder must not expire. `( sleep 3600 > fifo )` died mid-session once; the console
#      stopped accepting commands, `stop` never arrived, and it presented as a hung server. We use
#      `tail -f /dev/null`, which cannot time out.
#   3. Liveness is checked BY PORT. `pgrep -f` / `pkill -f` match the shell running them — that has
#      killed one of our own commands and reported an already-dead server as alive.
#
# It also cannot pass --auth-mode offline. That flag is accepted, boots clean, binds the port, and
# then refuses every client at login with "offline mode is only valid in singleplayer".
#
# Server STATE lives in .scratch/hytale-server/ (git-ignored — it holds cached credentials in
# auth.enc and hytale:Admin in permissions.json, so no /auth login device and no /op is needed).
# The BOOT LOGIC lives here, in git, so a clone is not missing a load-bearing file.
#
# Usage:
#   scripts/test-server.sh build     stage the mod jar into the server's mods/
#   scripts/test-server.sh start     boot (implies build) and wait for the port
#   scripts/test-server.sh status    report by port, not by process name
#   scripts/test-server.sh send ...  send one console command
#   scripts/test-server.sh stop      graceful stop, then release the FIFO holder
#   scripts/test-server.sh logs      tail the server log

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1
REPO_ROOT="$PWD"

SERVER_DIR="${HYTALE_SERVER_DIR:-$REPO_ROOT/.scratch/hytale-server}"
PORT="${HYTALE_PORT:-5520}"
FIFO="$SERVER_DIR/console.fifo"
LOG="$SERVER_DIR/server.log"
HOLDER_PID="$SERVER_DIR/.holder.pid"
BOOT_TIMEOUT="${HYTALE_BOOT_TIMEOUT:-180}"
STOP_TIMEOUT="${HYTALE_STOP_TIMEOUT:-60}"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
note() { printf '%s\n' "$*" >&2; }

# --- locating the game -------------------------------------------------------
# The launcher auto-updates without notice; do not pin a version anywhere.
find_game() {
    if [ -n "${HYTALE_HOME:-}" ]; then printf '%s\n' "$HYTALE_HOME"; return; fi
    local c
    for c in \
        "$HOME/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest" \
        "$HOME/.local/share/Hytale/install/release/package/game/latest"
    do
        [ -f "$c/Server/HytaleServer.jar" ] && { printf '%s\n' "$c"; return; }
    done
    return 1
}

find_java() {
    if [ -n "${HYTALE_JAVA:-}" ]; then printf '%s\n' "$HYTALE_JAVA"; return; fi
    local j
    for j in "$HOME"/.gradle/jdks/*25*/bin/java; do
        [ -x "$j" ] && { printf '%s\n' "$j"; return; }
    done
    command -v java >/dev/null 2>&1 && { command -v java; return; }
    return 1
}

# --- liveness: by port, never by process name --------------------------------
is_up() { ss -ltn 2>/dev/null | grep -q ":${PORT} "; }

wait_until() { # wait_until <up|down> <timeout>
    local want="$1" timeout="$2" waited=0
    while [ "$waited" -lt "$timeout" ]; do
        if [ "$want" = up ]; then is_up && return 0; else is_up || return 0; fi
        sleep 2; waited=$((waited + 2))
    done
    return 1
}

# --- commands ----------------------------------------------------------------
cmd_build() {
    note "==> building and staging the mod jar"
    ./gradlew :mod:hytale:shadowJar || die "shadowJar failed"
    mkdir -p "$SERVER_DIR/mods" || die "cannot create $SERVER_DIR/mods"
    # Stale jars from a previous build would both load.
    rm -f "$SERVER_DIR"/mods/*-all.jar
    cp mod/hytale/build/libs/*-all.jar "$SERVER_DIR/mods/" || die "no shadow jar to stage"
    note "    staged: $(ls -1 "$SERVER_DIR"/mods/*-all.jar | xargs -n1 basename | tr '\n' ' ')"
}

cmd_start() {
    is_up && die "something is already listening on :$PORT — stop it first (status/stop)"
    [ -d "$SERVER_DIR" ] || die "no server dir at $SERVER_DIR (it carries auth.enc; it is not created here)"
    [ -f "$SERVER_DIR/auth.enc" ] || note "warning: no auth.enc — the server may need '/auth login device'"
    [ -f "$SERVER_DIR/permissions.json" ] || note "warning: no permissions.json — a fresh dir starts UNPRIVILEGED, and the refusal reads exactly like a syntax error"

    local game java
    game="$(find_game)" || die "no Hytale install found; set HYTALE_HOME"
    java="$(find_java)" || die "no Java found; set HYTALE_JAVA"

    cmd_build

    rm -f "$FIFO"; mkfifo "$FIFO" || die "cannot create $FIFO"

    # Trap 2: a holder that cannot expire. Never `( sleep N > fifo )`.
    tail -f /dev/null > "$FIFO" &
    echo $! > "$HOLDER_PID"

    note "==> booting (log: $LOG)"
    # Trap 1: a real TTY, or the console silently ignores everything typed.
    # Trap: --auth-mode offline is deliberately absent and must stay absent.
    setsid script -qfc \
        "exec '$java' -jar '$game/Server/HytaleServer.jar' --assets '$game/Assets.zip' --disable-sentry --allow-op" \
        /dev/null < "$FIFO" > "$LOG" 2>&1 &

    if wait_until up "$BOOT_TIMEOUT"; then
        note "==> up on :$PORT — connect the client via Direct Connect to 127.0.0.1:$PORT"
    else
        note "==> NOT listening on :$PORT after ${BOOT_TIMEOUT}s; last log lines:"
        tail -20 "$LOG" >&2
        cmd_stop_holder
        exit 1
    fi
}

cmd_send() {
    [ $# -gt 0 ] || die "send needs a command"
    is_up || die "server is not listening on :$PORT"
    [ -p "$FIFO" ] || die "no console FIFO at $FIFO — was this server started by this script?"
    printf '%s\n' "$*" > "$FIFO"
    note "sent: $*"
}

cmd_stop_holder() {
    if [ -f "$HOLDER_PID" ]; then
        kill "$(cat "$HOLDER_PID")" 2>/dev/null
        rm -f "$HOLDER_PID"
    fi
    rm -f "$FIFO"
}

cmd_stop() {
    if is_up; then
        note "==> sending stop"
        [ -p "$FIFO" ] && printf 'stop\n' > "$FIFO"
        wait_until down "$STOP_TIMEOUT" || note "warning: still listening on :$PORT after ${STOP_TIMEOUT}s"
    else
        note "not listening on :$PORT"
    fi
    cmd_stop_holder
    note "==> holder released"
}

cmd_status() {
    if is_up; then
        printf 'SERVER_VERDICT: UP   | port %s listening\n' "$PORT"
    else
        printf 'SERVER_VERDICT: DOWN | port %s not listening\n' "$PORT"
    fi
    if [ -f "$HOLDER_PID" ] && kill -0 "$(cat "$HOLDER_PID")" 2>/dev/null; then
        printf '  console holder: alive (pid %s)\n' "$(cat "$HOLDER_PID")"
    else
        printf '  console holder: absent — the console will not accept commands\n'
    fi
    printf '  server dir:     %s\n' "$SERVER_DIR"
}

case "${1:-}" in
    build)  cmd_build ;;
    start)  cmd_start ;;
    stop)   cmd_stop ;;
    status) cmd_status ;;
    send)   shift; cmd_send "$@" ;;
    logs)   tail -f "$LOG" ;;
    *)      sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
