# The git-context mislaunch — fixed in the filesystem, not in a document

**Status: ADOPTED 2026-10-21.** Route chosen by the operator after three prior iterations of the
launch guard each improved *detection* and the defect recurred anyway. All three landed on
2026-10-21: the stub was created when the real instructions moved into this repository (PR #113),
rewritten the same day after a probe from the correct directory showed it raising a false alarm,
and rewritten again to import the real instructions rather than announce their absence (PR #124).
Sessions 19, 20 and 21 all began from the wrong directory regardless.

**Two parts.** Relocate the repository so the mislaunch is impossible; and guard git writes issued
from a directory that contains repositories while git has resolved to an outer one, so the same
configuration cannot misdirect a write anywhere else either.

**This is workspace-scope work by driver** — session launch, hook configuration and path
layout are dev-environment correctness, not Hytale features. It is recorded here because
the files it moves are this repository's, which is the file-location exception named in
`~/Workspace/CLAUDE.md` § "Security Work Is Workspace-Scope".

---

## The finding

Every prior fix edited a document that is read **after** the wrong command has already run.

The launch is a single line, recorded verbatim six or more times in `~/.local/share/fish/fish_history`:

```
cd ~/Workspace/Projects/Hytale/ && claude
```

Shell history recall — up-arrow, `ctrl-r` — hands that line back indefinitely, so it is
self-reinforcing. **No document in either repository can reach it.** The directory structure
made it the natural choice as well: `Projects/Hytale/` is named after the project and held five
repositories, while the actual project sat one level down under a different name.

Detection was never the gap. The `git rev-parse --show-toplevel` check added in PR #124 worked
correctly at session 21's start and reported the mislaunch. The gap is that detection's only
stated remedy is *"restart the session"*, which costs the whole session — so the session
continued mislaunched. That is the recurrence the operator reported.

### Corroborating evidence from the session that wrote this

Five cwd-drift incidents occurred while investigating, in a session whose entire subject was cwd
correctness. Three returned plausible wrong answers that were reported before being caught — a
directory listing read as the parent when it was the child, a `git ls-files` survey attributed to
the monorepo when it ran in `LegendaryContent`, and a second such survey. One failed loudly
(`exit 128`). One was correct by luck.

This is the measured shape already recorded in `[[Workspace_Observations]]`: Shell Invocation
Discipline rule 1 runs **25.7%** compliance because it fires on every call, while
`git checkout main` before `gh pr merge` is **36 for 36** as pure prose because it fires rarely.
**Frequency, not importance, predicts compliance.** Part 2 is designed around that finding.

---

## Part 1 — Relocation

`Projects/Hytale/` becomes the repository root. The history line above becomes correct
permanently, and the class of failure disappears rather than being detected.

### The move must be in-place

Moving each entry of `Legendary/` up, **not** renaming the parent directory.

This session's `claude` process and twelve MCP child processes hold
`cwd=/home/stephaneb/Workspace/Projects/Hytale`. Replacing that directory's inode would leave
every one of them on a deleted cwd. Nothing holds a cwd inside `Legendary/`, so moving its
contents up is safe.

A useful consequence: **the session performing the move has its own git context corrected in
place, with no restart.** One Gradle daemon (6h28m resident) is stopped first and re-provisions.

### Preconditions verified, not assumed

| Checked | Result |
|---|---|
| `.git` is a real directory, not a worktree pointer file | yes |
| `.git/worktrees` present | absent |
| `.gitmodules` present | absent |
| absolute paths in `.git/config` | none |
| `core.hooksPath` | global absolute, unaffected by the move |
| processes with a cwd inside `Legendary/` | none |
| `Projects/` tracked by the workspace repo | no — gitignored wholesale at `.gitignore:11` |

### Reference updates

72 external references to `Hytale/Legendary` exist, but only about six are live:

- `agent/health_check.py` (2) — includes the API-drift probe
- `agent/tests/test_health_check.py` (1), `agent/tests/test_session_update.py` (2)
- `~/Workspace/CLAUDE.md` (1), `Project_Dependencies.md` (1)
- `.ai/workspace_graph.json` (6) — already seven months stale; regenerate

Inside this repository, 39 references across 5 files, of which 5 are live:
[`CLAUDE.md`](../../../CLAUDE.md) (4) and
[`../../integration/live-server-testing.md`](../../integration/live-server-testing.md) (1).

**The remaining ~60 references are not updated.** They sit in observation logs and dated
planning documents, which are append-only records of what the path was at the time. Rewriting
them would falsify the record.

### Two things the move deletes outright

- **`Projects/Hytale/CLAUDE.md`**, the untracked stub. It collides with this repository's real
  `CLAUDE.md`, and the debt recorded against it — a load-bearing orientation document tracked by
  no repository — disappears with it.
- **The four archived working copies** (`LegendaryCore`, `LegendaryContent`, `LegendaryDogfood`,
  `LegendaryHytale`; 17.8M). Operator-authorized. They cannot remain: inside a repository root
  they would be nested untracked repositories that `git status` reports and `git add -A` would
  try to swallow.

#### The stash is the one irreversible loss, and it is bundled first

All commits and all 12 tags across the four repositories are reachable from a remote ref —
verified per-repository, including the one branch with no configured upstream
(`LegendaryContent/feature/anchored-harness-deterministic`, 0 unique commits).

One exception: `LegendaryContent` holds `stash@{0}`, dated 2026-02-08, *"anchored harness +
dogfood fixes (pre-branch)"* — 4 files, +299 −96. A stash belongs to no branch and is pushed
nowhere, and the GitHub repositories are archived read-only, so it cannot be pushed to safety.

It is near-certainly superseded: it predates the consolidation by six months and all four files
have live descendants here (`AnchoredTrialHostTick`, `AnchoredTrialHostDriver`,
`RecordingStormseekerHostRuntime` in two modules, both harness tests), reorganised into
`harness/` and `quests/stormseeker/` with the Lodestone rename applied. But *a descendant exists*
is not *this content is contained in it*, so it is bundled (~15KB) before deletion.

**Why this is in the spec at all:** `git status --porcelain` returned a clean zero for all four
repositories. That zero is correct and answers the wrong question — it sees working-tree changes,
not unpushed refs. It is the exact shape of
`[[Workspace_Observations#2026-10-21 PATTERN: a verification surface that cannot show the answer returns a clean zero, and the zero is believed]]`,
reached independently one day later. The generalisable question is not *"was the search
filtered?"* but *"could the thing I searched have contained the answer?"*

---

## Part 2 — A guard on misdirected git commands

A `PreToolUse` Bash hook. It denies a git **write** subcommand — `commit`, `push`, `checkout`,
`branch`, `merge`, `tag`, `reset` — when all three of these hold:

1. the command does not pin a repository with `git -C <abs>`;
2. the resolved `--show-toplevel` for the hook's reported cwd is **not** the cwd itself; and
3. the cwd has an immediate child directory containing a `.git`.

That is: *you are standing in a directory that contains repositories, while git has resolved
you to an outer one.* The denial message names the child repositories found and the explicit
`git -C <abs>` form.

### Measured behaviour of the condition

| cwd | toplevel is cwd | child repos | verdict |
|---|---|---|---|
| `~/Workspace` | yes | `jcodemunch-mcp`, `jdocmunch-mcp` | allow |
| `~/Workspace/Projects` | no | `PermitKit` | **deny** |
| `~/Workspace/Projects/Hytale` (today) | no | `Legendary` + 4 archived | **deny** |
| `~/Workspace/Projects/Hytale` (after Part 1) | yes | none | allow |
| `.../Hytale/Legendary` | yes | none | allow |
| `.../Hytale/Legendary/docs` | no | none | allow |
| `~/Workspace/Projects/PermitKit` | yes | none | allow |
| `~/Workspace/agent` | no | none | allow |

No false positive at the workspace root, where two submodules are immediate children but the
toplevel *is* the cwd. No false positive in a subdirectory of a correctly-resolved repository.

### Two conditions considered and rejected, both for recorded reasons

**A required leading `cd <abs> &&`.** This is precisely `cwd_guard` v1's design, which had to be
withdrawn: the harness **strips** a leading `cd <path> &&` whenever `<path>` equals the current
cwd, so a fully compliant command reaches the hook with the prefix already gone and gets denied
for lacking what was in fact written. The form is not reliably observable, so the guard checks a
*condition* instead — the same correction `cwd_guard` v2 already made.

**`git check-ignore` on the cwd.** Appealing and nearly shipped: from `Projects/Hytale` it
returns `.gitignore:11:Projects/`, a clean positive. But it is silent for `Projects/Android`,
because **three files under `Projects/Android/JobLabelScanner/.planning/` are tracked by the
workspace repo** and tracked content overrides `.gitignore`, so git declines to call the path
ignored. A condition that goes quiet wherever someone happened to track a file is a guard with an
invisible hole — the same defect as
`[[Hytale_Observations#2026-10-21 SMELL: the docs guard shipped with the exact gap it exists to prevent]]`,
caught this time before shipping rather than after.

### What it does not catch, stated rather than implied

**Subdirectory drift inside one repository is not a git hazard at all.** Git resolves upward to
the same root, so `cd docs && git commit` commits to the right repository. An earlier draft of
this document claimed Part 2 covered that case and justified it with the five drift incidents
below; that was wrong, and the claim is corrected rather than quietly dropped.

The five drifts produced wrong **reads**, not misdirected writes — a directory listing, and two
`git ls-files` surveys attributed to the monorepo while running in `LegendaryContent`. That last
shape is **not mechanisable**: the cwd was a legitimate repository root and git answered correctly
about it. Only intent was wrong, and no hook can see intent. The honest mitigation there is the
`toplevel:` echo used throughout this session's later commands.

So Part 2's actual value is generalising Part 1 beyond Hytale: `Projects/` itself trips the
condition today, and any future project laid out as a parent directory over a repository would too.

### Relationship to `cwd_guard`

`agent/cwd_guard.py` already runs as a `PreToolUse` Bash hook in shadow mode, and its shadow log
has reached 2,575 calls across 14 root values — firing Workspace Deferral #19's promotion trigger,
with its second trigger ("a real cwd-drift incident is noticed in session") fired five times over
in this session.

`cwd_guard` **cannot** catch the git case: it checks whether relative *path tokens* resolve, and a
bare `git commit -m "..."` carries no path token. The two guards are complementary, and this spec
does not decide `cwd_guard`'s promotion.

---

## Verification

Part 1 is complete when, with no reference to this document:

1. `git rev-parse --show-toplevel` from `~/Workspace/Projects/Hytale` prints that same path.
2. `./gradlew build` is green and `CENSUS_VERDICT:` / `COVERAGE_VERDICT:` / `DOCS_VERDICT:` each
   read GREEN — **by name, never by position**. `COVERAGE_VERDICT` must read `0 exempt`, which is
   what shows `mod/hytale` compiled against the real jar.
3. `python agent/health_check.py` passes, including the `Hytale: API drift` row — which exercises
   a relocated path and so is the load-bearing check here.
4. The agent test suite passes (588 tests), covering the two updated test files.

Part 2 is complete when every row of the condition table above is reproduced by the shipped hook
— **including the four `allow` rows**, not only the denials. A guard is green on first write and
that proves nothing; each row is a deliberate mutation of the inputs, which is the only reason the
verdicts are believed. That lesson is this project's own: see
`[[Hytale_Observations#2026-10-21 SMELL: the docs guard shipped with the exact gap it exists to prevent]]`.

The row that matters most is `~/Workspace` → **allow**. Two submodules sit as immediate children
there, so a condition written only against the denial cases would have broken every git command at
the workspace root.

## What this does not do

- It does not delete or rewrite the ~60 historical path references.
- It does not promote `cwd_guard` out of shadow mode.
- It does not change Shell Invocation Discipline rule 1, whose amendment remains unauthorized and
  has now been proposed by three consecutive sessions.
- It does not touch `~/Workspace/CLAUDE.md` beyond the single stale path reference.
