# Live-probe drivers

Each script here boots the test server, issues one probe command from the console,
and prints the result. They exist because a probe is only evidence if someone can
re-run it.

## Why these are tracked and the server directory is not

The drivers are tracked. The harness they drive — `.scratch/hytale-server/` — is
**deliberately not**: it holds `auth.enc` (server OAuth credentials) and a
generated `universe/` that regenerates free on the next boot. Per-session copies of
it had reached 432M by 2026-08-25.

Each driver resolves the harness at `../../.scratch/hytale-server` and fails loudly
if it is absent. Override with `HYTALE_TEST_SERVER=/path/to/server`.

**These were untracked until 2026-10-20**, which is the reason this directory
exists. On that day §5.1 of the 0.7.2 capability assessment recorded measured
figures produced by `drive-weatheroverrideprobe-client.sh` while that script was
git-ignored — the same shape as the `2026-10-20 DEBT` observation about
`.scratch/n12-scan/`, which took three specs' numbers with it when it vanished. A
measurement whose driver is untracked can be neither reproduced nor checked; it can
only be believed or replaced.

**The last two were promoted 2026-10-21** — `drive-signalprobe.sh` and `server-up.sh` — and that
promotion found a second instance of the same hazard, one directory over. The harness still held
**diverged duplicates** of six already-tracked drivers, differing only in lacking the `SERVER_DIR`
resolution block above. Every run that session had used the untracked copies, which means the
tracked ones — the only ones with path resolution — **had never been executed at all**. That was
fixed by running `drive-signalprobe.sh` from this directory and confirming the harness resolved and
the log landed there rather than here; then the duplicates were deleted. They were not on the
keep-list in `docs/integration/live-server-testing.md`, so the project's own cleanup guidance
already treated them as disposable — what it had not anticipated was them being *run* in preference
to the tracked originals. **If you find a `drive-*.sh` inside the harness directory, it is a stale
shadow of a tracked file; delete it rather than run it.**

## Verification status

A driver is a hypothesis about an engine that updates every 2–6 weeks. Re-verify by
executing, not by reading.

| Driver | Probe | Last run green | Against |
|---|---|---|---|
| `drive-blockspoofprobe-client.sh` | `/blockspoofprobe` | 2026-10-20 (partial) | 0.7.2 |
| `drive-blockspoofprobe.sh` | `/blockspoofprobe` (headless subset) | 2026-10-20 | 0.7.2 |
| `drive-weatheroverrideprobe-client.sh` | `/weatheroverrideprobe` | 2026-10-20 | 0.7.2 |
| `drive-weatheroverrideprobe.sh` | `/weatheroverrideprobe` (headless; expects SKIPPED) | 2026-10-20 | 0.7.2 |
| `drive-beamprobe.sh` | `/beamprobe` | 2026-10-20 | 0.7.2 |
| `drive-render-session-client.sh` | `/beamprobe` + `/markerprobe` | 2026-10-20 | 0.7.2 |
| `drive-pointprobe.sh` | `/pointprobe` | 2026-10-20 | 0.7.2 |
| `drive-signalprobe.sh` | `/signalprobe` | 2026-10-21 | 0.7.2 |
| `drive-blockprobe.sh` | `/blockplacementprobe` | 2026-08-29 | **0.6.1 — unverified since** |
| `drive-prefabprobe.sh` | `/prefabprobe` | 2026-08-29 | **0.6.1 — unverified since** |

`server-up.sh` is **not** in the table because it is not a probe driver — see below.

The last two predate the 0.7.2 migration and are promoted as-is, labelled rather
than silently endorsed. `/prefabprobe` in particular is known to have changed
behaviour: `World.waitForFutureWithoutLock` was removed in 0.7.2, so it is now an
async chain and **no longer prints when it returns**.

`drive-render-session-client.sh` ran green on 2026-10-20 and closed both of §8's
rendering questions — beams render, and the `spawn`-returns-null anomaly was a headless
artifact (`5 VALID / 0 null` with a client attached). It was committed the same day
marked **never run**, which was correct at the time: compiling against the real jar is
strictly weaker than having executed, and this table exists because a driver is a
hypothesis until it runs.

**Its first run exposed three defects in the instruments, which is the point of running
them.** One was an ordering bug in this driver: `/beamprobe` fired *first*, and its
holders have a 60s lifetime, so the beams expired within seconds of the 15-minute hold
beginning — the window that exists purely so a human can look at them did not overlap
their lifetime. `/beamprobe` now fires **last**, immediately before the hold. The other
two were in `/markerprobe` and are recorded in its javadoc: it graded its own
non-idempotence as an engine failure, and its assertion details restated expectations
instead of observed values, so a FAIL line read `isRevealed == false` on a call that had
returned `true`.

## `server-up.sh` — a third shape

The drivers above boot, fire, and stop. `drive-blockspoofprobe-client.sh` boots, fires, and
**holds**. `server-up.sh` boots and **holds without firing anything**, leaving a console FIFO at
`.scratch/hytale-server/console` that accepts commands for as long as the server lives:

```bash
bash scripts/live-probes/server-up.sh
echo signalprobe > .scratch/hytale-server/console
echo stop        > .scratch/hytale-server/console
```

It exists because the fire-and-stop shape is wrong whenever a human must get into position
*between* boot and probe. `/signalprobe` is the case that forced it: its decisive readings need a
player standing somewhere specific — underground, beside a light source, at the surface at a
chosen time of day — and a driver that fires on a timer either probes before the operator has
arrived or guesses how long they will take. On 2026-10-21 the same server was held open while five
readings were taken at five positions, which is five client joins the fire-and-stop shape would
have cost.

Two details in it were learned the hard way and are not obvious:

- **The FIFO holder is `tail -f /dev/null`, not `sleep N`.** A sleep-based holder expired
  mid-session once; the console then silently stopped accepting commands and the failure presented
  as a hung server.
- **The readiness check reads `ss -lun`, not `ss -ltn`.** Hytale's transport is QUIC, i.e. **UDP**,
  so the TCP-only form reports `NOT LISTENING` for a perfectly healthy server. That false negative
  was reported to the operator as "the server is down" before it was caught.

## Client-attached vs headless

Most probes answer headless. Two classes do not:

- **Anything whose subject is a player.** `/weatheroverrideprobe` operates on a
  player's `WeatherTracker` and observes a packet to that player's connection. With
  no client it reports `SKIPPED`, never `PASS` — that is no run, not a degraded one.
- **Anything whose observable is rendering.** `/beamprobe` can confirm the server
  accepted a beam; only a client can confirm it was drawn. `/markerprobe` likewise
  confirms the discovery gate flipped, never that an icon appeared on the map.

`drive-render-session-client.sh` is the driver for that second class, and it is the
one script here that fires **two** probes. The pairing is not convenience: the scarce
resource for a rendering question is a human at a connected client, not the server, so
the right unit of work is one join per *hold window* rather than one per probe.
Splitting `/beamprobe` and `/markerprobe` would make the operator connect twice to
answer two halves of the same question. It also fires `/markerprobe` **twice** —
`WorldMapManager` is a `TickingThread`, so a registered `MarkerProvider` publishes
asynchronously and the first invocation cannot observe its own effect. An absent
marker on run 1 is expected; on run 2 it is a finding.

`drive-weatheroverrideprobe-client.sh` waits for `Player joined:` and fires the
probe itself, so there is no window to mistime.

`drive-blockspoofprobe-client.sh` is the exception to the fire-and-stop shape: it
runs the probe and then **holds the server open** (default 900s, overridable as
`$1`) because that probe's decisive test is a list of things only a human can
observe — does the spoofed block render, does it survive a neighbour change, a
chunk reload, a relog. Nothing is measured during the hold, and the probe says so
rather than implying its silence is a result. It fires from the **console**, not
chat: probe output runs to ~60 lines and `context.sendMessage` goes to whoever
issued the command, which in a chat box is unreadable.

## Two failure modes that cost a session each

**Server-side auth expiry.** The server authenticates against Hytale's OAuth
service on its own behalf, and a stored refresh token expires (observed: 52 days).
The server then boots, binds the port, accepts the connection and validates the
*client's* token — then refuses the login with `serverAuthUnavailable`. It presents
as the client's failure. Headless probes never see it, because server auth is only
consulted at a client login. Remedy: run `run-server.sh` interactively, then
`/auth login device` and approve the code in a browser. The client driver now
fails fast on this instead of waiting out its join timeout.

**A server already on 5520.** Usually the interactive one used for that auth. The
drivers need FIFO console control, so they must own the boot; the client driver
pre-checks the port and says so rather than failing on bind.

Full operating notes, including the console TTY/FIFO requirements and the boot
flags that do not do what they look like: `docs/integration/live-server-testing.md`.
