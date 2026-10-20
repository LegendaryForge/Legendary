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
| `drive-pointprobe.sh` | `/pointprobe` | 2026-10-20 | 0.7.2 |
| `drive-blockprobe.sh` | `/blockplacementprobe` | 2026-08-29 | **0.6.1 — unverified since** |
| `drive-prefabprobe.sh` | `/prefabprobe` | 2026-08-29 | **0.6.1 — unverified since** |

The last two predate the 0.7.2 migration and are promoted as-is, labelled rather
than silently endorsed. `/prefabprobe` in particular is known to have changed
behaviour: `World.waitForFutureWithoutLock` was removed in 0.7.2, so it is now an
async chain and **no longer prints when it returns**.

## Client-attached vs headless

Most probes answer headless. Two classes do not:

- **Anything whose subject is a player.** `/weatheroverrideprobe` operates on a
  player's `WeatherTracker` and observes a packet to that player's connection. With
  no client it reports `SKIPPED`, never `PASS` — that is no run, not a degraded one.
- **Anything whose observable is rendering.** `/beamprobe` can confirm the server
  accepted a beam; only a client can confirm it was drawn.

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
