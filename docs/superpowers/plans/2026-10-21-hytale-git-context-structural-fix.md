# Hytale Git-Context Structural Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `cd ~/Workspace/Projects/Hytale/ && claude` — the line in the operator's shell history — correct permanently, and make a git write issued from a directory that contains repositories impossible.

**Architecture:** Two independent parts. Part 1 relocates this repository's contents one level up so `Projects/Hytale/` *is* the repository root, performed as an in-place entry-by-entry move because this session and twelve MCP children hold `cwd=Projects/Hytale` and would be stranded by a parent rename. Part 2 adds `agent/git_repo_guard.py`, a `PreToolUse` Bash hook mirroring `agent/cwd_guard.py`'s contract, which denies a git write when the effective cwd resolves to an outer repository while containing child repositories of its own.

**Tech Stack:** Python 3 (hook + pytest), Gradle 9.2 / Java 25 (verification gates), git, fish (operator shell).

## Global Constraints

- Design record: [`../specs/2026-10-21-hytale-git-context-structural-fix-design.md`](../specs/2026-10-21-hytale-git-context-structural-fix-design.md). Do not re-derive decisions from it; it is adopted.
- **Read every gate by its verdict line, never by exit code or line position.** `CENSUS_VERDICT:`, `COVERAGE_VERDICT:`, `DOCS_VERDICT:`. Invoke a gate bare; to keep output, redirect to a file as the whole command and read the file in a separate call. An **absent** verdict line is a failure, not a pass.
- **`COVERAGE_VERDICT` must read `0 exempt`** — that is what proves `mod/hytale` compiled against the real game jar.
- **Every Bash call opens with its own absolute `cd`.** Five drift incidents occurred while researching this change; three returned plausible wrong answers.
- `main` accepts no direct pushes (`enforce_admins: true`). Every change arrives by PR, merged with `--merge` (never squash or rebase), and `git checkout main` runs **before** `gh pr merge`.
- Do **not** rewrite the ~60 historical path references in observation logs and dated planning documents. They record what the path was at the time.
- Do **not** update `CLAUDE.md:191`, which quotes the dead Spotless hook's already-broken path `/home/stephaneb/Projects/Hytale/Legendary/gradlew`. It documents a defect; correcting it would erase the point.

---

## File Structure

| File | Responsibility |
|---|---|
| `Knowledge/MainVault/GameDev/Hytale/legendarycontent-stash-2026-02-08.bundle` | Create. Insurance for the only unique bytes in the four archived repos. Tracked by the workspace repo; invisible to the vault linter, which globs `*.md` only. |
| `~/Workspace/Projects/Hytale/*` | 24 entries moved up from `Legendary/`. |
| `Legendary/CLAUDE.md` → `Projects/Hytale/CLAUDE.md` | Modify. Launch section is rewritten: the repository root and the natural launch directory are now the same path, so the whole mislaunch apparatus is deleted rather than reworded. |
| `docs/integration/live-server-testing.md:48` | Modify. One `cd` path in the server-harness recipe. |
| `agent/health_check.py:40,72` | Modify. `SESSION_DOC_SOURCES["Hytale"]` and `HYTALE_REPO` (the API-drift probe). |
| `agent/tests/test_session_update.py:127,153` | Modify. Two commit-path fixtures. |
| `agent/tests/test_health_check.py:862` | Modify. One absolute path fixture. |
| `~/Workspace/CLAUDE.md:277` | Modify. Active-projects row: path, and the now-false launch warning. |
| `Knowledge/MainVault/Systems/AIWorkspace/Project_Dependencies.md:16` | Modify. One path. |
| `agent/git_repo_guard.py` | Create. The Part 2 hook. Pure Python, no subprocess: walks ancestors for `.git` exactly as `cwd_guard.find_project_root` does. |
| `agent/tests/test_git_repo_guard.py` | Create. Eleven cases — every row of the condition table, allow rows included. |
| `~/.claude/settings.json` | Modify. One `PreToolUse` Bash hook entry. |

---

## Task 1: Preserve the stash, then delete the four archived copies

**Files:**
- Create: `~/Workspace/Knowledge/MainVault/GameDev/Hytale/legendarycontent-stash-2026-02-08.bundle`
- Delete: `Projects/Hytale/{LegendaryCore,LegendaryContent,LegendaryDogfood,LegendaryHytale}`

**Interfaces:**
- Consumes: nothing.
- Produces: a `Projects/Hytale/` containing only `CLAUDE.md` (stub) and `Legendary/`, which Task 2 requires.

- [ ] **Step 1: Re-verify that nothing but the stash is unique**

```bash
for d in LegendaryCore LegendaryContent LegendaryDogfood LegendaryHytale; do printf '%-18s unique=' "$d"; git -C "$d" rev-list --count --all --not --remotes; done
```

Expected: `LegendaryContent unique=3`, the other three `unique=0`. The 3 are the stash's own commit, index commit and untracked-files commit — nothing else. **If any other repository reports non-zero, stop and report;** the authorization was given on the premise that it would not.

- [ ] **Step 2: Create the bundle**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/LegendaryContent && git bundle create /home/stephaneb/Workspace/Knowledge/MainVault/GameDev/Hytale/legendarycontent-stash-2026-02-08.bundle refs/stash
```

- [ ] **Step 3: Verify the bundle is readable and carries the stash**

```bash
cd /home/stephaneb/Workspace && git bundle verify Knowledge/MainVault/GameDev/Hytale/legendarycontent-stash-2026-02-08.bundle
```

Expected: `The bundle is okay` (or `bundle records a complete history`), and the listed ref is `refs/stash`. A bundle that verifies only against the source repository is not insurance — confirm the output names `refs/stash` explicitly.

- [ ] **Step 4: Prove the work is recoverable from the bundle alone**

```bash
cd /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad && rm -rf bundletest && git init -q bundletest && git -C bundletest fetch -q /home/stephaneb/Workspace/Knowledge/MainVault/GameDev/Hytale/legendarycontent-stash-2026-02-08.bundle refs/stash:refs/recovered && git -C bundletest show --stat --format='%s' refs/recovered
```

Expected: subject `On master: anchored harness + dogfood fixes (pre-branch)` and the 4-file stat (`+299 −96`). **This step is the whole point of Task 1** — a bundle nobody has restored from is an untested backup.

- [ ] **Step 5: Commit the bundle**

```bash
cd /home/stephaneb/Workspace && git commit --only Knowledge/MainVault/GameDev/Hytale/legendarycontent-stash-2026-02-08.bundle -m "$(cat <<'MSG'
chore(hytale): bundle the one unique ref before the archived copies are deleted

LegendaryContent stash@{0} (2026-02-08, "anchored harness + dogfood fixes")
is the only object in the four archived working copies that is reachable from
no remote ref. The GitHub repos are archived read-only, so it cannot be pushed
to safety. Restored from the bundle and diffed before committing.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
MSG
)"
```

`--only`, not `git add` — another session may share this index. See `CLAUDE.md` § Vault Commit Discipline.

- [ ] **Step 6: Delete the four archived copies**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && rm -rf LegendaryCore LegendaryContent LegendaryDogfood LegendaryHytale && ls -A
```

Expected remaining: `CLAUDE.md  Legendary`.

---

## Task 2: Relocate the repository in place

**Files:**
- Delete: `Projects/Hytale/CLAUDE.md` (the untracked stub)
- Move: all 24 entries of `Projects/Hytale/Legendary/` → `Projects/Hytale/`
- Delete: the emptied `Projects/Hytale/Legendary/`

**Interfaces:**
- Consumes: Task 1's cleaned `Projects/Hytale/`.
- Produces: `git rev-parse --show-toplevel` from `~/Workspace/Projects/Hytale` printing that same path. Tasks 3–6 all assume it.

- [ ] **Step 1: Stop the Gradle daemon**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew --stop
```

It holds project paths and ~1GB resident; it re-provisions on next use. Moving the tree under a live daemon is what makes the first post-move build fail confusingly.

- [ ] **Step 2: Record the pre-move identity to compare against**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && git rev-parse HEAD && git rev-parse --abbrev-ref HEAD && git status --porcelain | wc -l
```

Note all three. The HEAD sha must be identical after the move; the move is a filesystem operation and must not change a single object.

- [ ] **Step 3: Delete the untracked stub**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && rm -f CLAUDE.md && ls -A
```

It collides with the real `CLAUDE.md` arriving in Step 4, and it is tracked by no repository — which is the debt it carried. Expected remaining: `Legendary`.

- [ ] **Step 4: Move every entry up, dotfiles included**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && for e in $(ls -A Legendary); do mv "Legendary/$e" .; done; ls -A | sort | tr '\n' ' '; echo; echo "entries now: $(ls -A | wc -l)"
```

`ls -A` excludes `.` and `..`, which is what makes this safe — a `Legendary/.*` glob would match them and error. Expected 24 entries, including `.git`, `.github`, `.gitignore`, `.gradle`, `.claude`, `.scratch`, `.superpowers`.

**Do not** `mv Projects/Hytale/Legendary` to a new name and swap: that replaces `Projects/Hytale`'s inode and strands the thirteen processes holding it as their cwd, this session among them.

- [ ] **Step 5: Remove the emptied directory**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && rmdir Legendary && ls -A Legendary 2>&1 || echo "Legendary/ is gone"
```

`rmdir` not `rm -rf`: it refuses a non-empty directory, so it doubles as the assertion that Step 4 moved everything.

- [ ] **Step 6: Verify repository identity is unchanged and the context is now correct**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && echo "toplevel: $(git rev-parse --show-toplevel)" && echo "head: $(git rev-parse HEAD)" && echo "branch: $(git rev-parse --abbrev-ref HEAD)" && echo "dirty: $(git status --porcelain | wc -l)"
```

Expected: toplevel `/home/stephaneb/Workspace/Projects/Hytale`; head and branch **identical** to Step 2; dirty count equal to Step 2's plus the two new documents. A changed HEAD means something other than a move happened — stop.

---

## Task 3: Repoint this repository's own references and retire the launch apparatus

**Files:**
- Modify: `Projects/Hytale/CLAUDE.md` (lines 9, 29, 53 and the whole launch blockquote)
- Modify: `Projects/Hytale/docs/integration/live-server-testing.md:48`

**Interfaces:**
- Consumes: Task 2's relocated tree.
- Produces: a `CLAUDE.md` whose launch section describes one directory, so no future session can read a two-directory rule out of it.

- [ ] **Step 1: Replace the launch blockquote**

The existing section (`> ## Launch Hytale sessions from this directory` through the paragraph ending `something never seen.`) is deleted entirely and replaced with:

```markdown
> ## One directory, and it is the repository root
>
> `~/Workspace/Projects/Hytale` — the directory this file is in, and the git repository root.
>
> ```bash
> git rev-parse --show-toplevel     # must print …/Projects/Hytale
> ```
>
> **Until 2026-10-21 this was two directories and that was the defect.** The repository sat at
> `Projects/Hytale/Legendary` while `Projects/Hytale` — named after the project, and the directory
> the operator's shell history actually held — resolved git to the *workspace* repo. Three
> iterations of a launch guard were written, all on 2026-10-21, each improving detection; sessions
> 19, 20 and 21 began from the wrong directory regardless. The fourth fix moved the repository
> instead, so the recalled command became correct and the condition stopped existing.
>
> **Do not reintroduce a launch guard here.** There is nothing left to guard: the natural launch
> directory and the repository root are the same path. A guard whose condition cannot occur is
> read as a live hazard by the next session and costs it the time to disprove. The `@Legendary/CLAUDE.md`
> import stub in the parent directory is deleted for the same reason.
>
> Design: `docs/superpowers/specs/2026-10-21-hytale-git-context-structural-fix-design.md`. (Written as a code span, not a link: a relative link here would be correct in `CLAUDE.md` and broken in this plan, and `checkDocsOrganization` rule 3 rejects it — which is how this line was found.)
```

- [ ] **Step 2: Repoint the two remaining prose paths**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && sed -i '53s|`Projects/Hytale/Legendary` is the|`Projects/Hytale` is the|' CLAUDE.md && sed -n '53p' CLAUDE.md
```

Then the repo-layout code block near line 57: its first line reads `Legendary/` and becomes `Hytale/`. Edit by hand — it is inside a fenced block and `sed` on a line number is brittle here.

- [ ] **Step 3: Confirm line 191 was NOT touched**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && grep -n 'SPOTLESS_EXECUTOR' -A2 CLAUDE.md | grep 'home/stephaneb/Projects'
```

Expected: the broken path `/home/stephaneb/Projects/Hytale/Legendary/gradlew` still present verbatim. It documents the dead hook's defect; "fixing" it destroys the record.

- [ ] **Step 4: Repoint the server-harness recipe**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && sed -i '48s|/home/stephaneb/Workspace/Projects/Hytale/Legendary|/home/stephaneb/Workspace/Projects/Hytale|' docs/integration/live-server-testing.md && sed -n '48p' docs/integration/live-server-testing.md
```

- [ ] **Step 5: Verify no live self-reference survives**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && git grep -n 'Projects/Hytale/Legendary' -- CLAUDE.md docs/integration/ docs/README.md || echo "no live self-references remain"
```

Expected: only `CLAUDE.md:191`. Dated documents under `docs/superpowers/plans/` keep theirs — including this file.

- [ ] **Step 6: Run the docs gate bare, then read its verdict**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew checkDocsOrganization > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t3-docs.log 2>&1
```

- [ ] **Step 7: Read the verdict line by name**

```bash
grep DOCS_VERDICT /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t3-docs.log || echo "VERDICT ABSENT -- FAILURE"
```

Expected `DOCS_VERDICT: GREEN`. Rule 3 (relative links resolve) is the one at risk — Step 1 added a link from `CLAUDE.md` into `docs/`.

- [ ] **Step 8: Commit on a branch**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && git checkout -b fix/git-context-structural && git commit --only CLAUDE.md docs/integration/live-server-testing.md docs/superpowers/specs/2026-10-21-hytale-git-context-structural-fix-design.md docs/superpowers/plans/2026-10-21-hytale-git-context-structural-fix.md -m "$(cat <<'MSG'
fix: the repository is the launch directory, so the mislaunch cannot recur

Projects/Hytale/Legendary moved to Projects/Hytale. The operator's shell
history holds `cd ~/Workspace/Projects/Hytale/ && claude`, recalled
indefinitely by up-arrow and ctrl-r, and no document could reach it — all
three prior guards were read after the wrong command had already run.

The launch blockquote is deleted rather than reworded: its condition no
longer exists, and a guard that cannot fire reads as a live hazard.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

## Task 4: Repoint workspace references and regenerate the graph

**Files:**
- Modify: `agent/health_check.py:40,72`
- Modify: `agent/tests/test_session_update.py:127,153`
- Modify: `agent/tests/test_health_check.py:862`
- Modify: `~/Workspace/CLAUDE.md:277`
- Modify: `Knowledge/MainVault/Systems/AIWorkspace/Project_Dependencies.md:16`
- Regenerate: `.ai/workspace_graph.json`

**Interfaces:**
- Consumes: Task 2's relocated tree.
- Produces: a passing `Hytale: API drift` health-check row, which is the only automated check that exercises the moved path.

- [ ] **Step 1: Write the failing assertion first — run the health check and watch the Hytale row break**

```bash
cd /home/stephaneb/Workspace && python agent/health_check.py > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t4-pre.log 2>&1
```

- [ ] **Step 2: Confirm it fails for the right reason**

```bash
grep -E 'Hytale|Session Doc' /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t4-pre.log
```

Expected: `Hytale: API drift` is no longer PASS, because `HYTALE_REPO` points at a path that no longer exists. If it still reads PASS, the row is not keyed to the path the way the design assumes — stop and re-read `agent/health_check.py:72`.

- [ ] **Step 3: Repoint the two code paths**

```bash
cd /home/stephaneb/Workspace && sed -i '40s|Workspace/Projects/Hytale/Legendary|Workspace/Projects/Hytale|' agent/health_check.py && sed -i '72s|Projects/Hytale/Legendary|Projects/Hytale|' agent/health_check.py && sed -n '40p;72p' agent/health_check.py
```

- [ ] **Step 4: Repoint the three test fixtures**

```bash
cd /home/stephaneb/Workspace && sed -i 's|Projects/Hytale/Legendary/core/x.java|Projects/Hytale/core/x.java|g' agent/tests/test_session_update.py && sed -i '862s|Projects/Hytale/Legendary/mod/hytale|Projects/Hytale/mod/hytale|' agent/tests/test_health_check.py && grep -n 'Projects/Hytale' agent/tests/test_session_update.py agent/tests/test_health_check.py
```

- [ ] **Step 5: Run the agent suite**

```bash
cd /home/stephaneb/Workspace && python -m pytest agent/tests/ -q > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t4-tests.log 2>&1
```

- [ ] **Step 6: Read the result**

```bash
tail -5 /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t4-tests.log
```

Expected: 588 passed. Any failure naming a Hytale path means a fixture was missed.

- [ ] **Step 7: Re-run the health check and confirm the Hytale row recovered**

```bash
cd /home/stephaneb/Workspace && python agent/health_check.py > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t4-post.log 2>&1
```

- [ ] **Step 8: Read both Hytale rows and the summary**

```bash
grep -E 'Hytale|^Summary' /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t4-post.log
```

Expected: `Hytale: API drift .... PASS (mod/hytale compiles against installed jar)` and `Session Doc: Hytale .... PASS`. **`INFO` is not success** — that is the no-game-jar state, and it would mean the probe is now compiling nothing.

- [ ] **Step 9: Update the two prose paths**

`~/Workspace/CLAUDE.md:277` — the Hytale row's path becomes `Projects/Hytale/`, its CLAUDE.md cell becomes `Projects/Hytale/CLAUDE.md`, and the trailing warning (*"launch Hytale sessions from that directory; it does not auto-load from `Projects/Hytale/`, and the miss is silent"*) is **deleted**: it describes a condition that no longer exists. Replace with `— the repository root is the launch directory (relocated 2026-10-21)`.

`Project_Dependencies.md:16` — `` `Projects/Hytale/Legendary` is a single Gradle multi-project `` becomes `` `Projects/Hytale` is a single Gradle multi-project ``. Add one sentence to that section: the four archived working copies no longer exist locally, and the bundle path that preserves the one unique ref.

- [ ] **Step 10: Regenerate the workspace graph**

```bash
cd /home/stephaneb/Workspace && python agent/workspace_relationships.py && grep -c 'Hytale/Legendary' .ai/workspace_graph.json || echo "0 stale Hytale paths in graph"
```

Nothing invokes this script on a schedule; the on-disk copy was dated 2026-03-10 before this. Expected: 0 matches.

- [ ] **Step 11: Commit, workspace scope, routine and substantive split**

```bash
cd /home/stephaneb/Workspace && git commit --only agent/health_check.py agent/tests/test_session_update.py agent/tests/test_health_check.py CLAUDE.md Knowledge/MainVault/Systems/AIWorkspace/Project_Dependencies.md .ai/workspace_graph.json -m "$(cat <<'MSG'
fix(agent): repoint Hytale paths after the repository moved up one level

Projects/Hytale/Legendary -> Projects/Hytale. The load-bearing reference is
HYTALE_REPO at health_check.py:72: the API-drift probe is the only automated
check that compiles mod/hytale against the real game jar, and it reads INFO
rather than PASS when the path is wrong -- verified broken, then fixed.

Drops the launch warning from the Active projects table: the repository root
and the launch directory are now the same path.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

## Task 5: The git repository guard

**Files:**
- Create: `~/Workspace/agent/git_repo_guard.py`
- Test: `~/Workspace/agent/tests/test_git_repo_guard.py`
- Modify: `~/.claude/settings.json`

**Interfaces:**
- Consumes: nothing from earlier tasks. Independently testable.
- Produces:
  - `find_repo_root(cwd: str) -> str` — nearest ancestor containing `.git`; `cwd` itself if none.
  - `child_repos(cwd: str) -> list[str]` — sorted names of immediate children containing `.git`.
  - `effective_cwd(command: str, cwd: str) -> str` — `cwd` adjusted by a leading absolute `cd`.
  - `is_pinned(command: str) -> bool` — True when a `git -C <path>` appears.
  - `git_write_subcommands(command: str) -> list[str]` — write subcommands found, heredoc text masked.
  - `verdict(command: str, cwd: str) -> list[str] | None` — child repo names when the call must be denied, else `None`.
  - `main(stdin=None, stdout=None) -> int` — hook entrypoint, same contract as `cwd_guard.main`.

- [ ] **Step 1: Write the failing tests**

```python
"""Tests for git_repo_guard — every row of the design's condition table."""
import json
import io
import os
import pytest

from agent import git_repo_guard as g


def _repo(path):
    os.makedirs(os.path.join(path, ".git"), exist_ok=True)
    return path


@pytest.fixture
def tree(tmp_path):
    """outer/            <- a repo
         parent/         <- NOT a repo, holds a child repo
           inner/        <- a repo
         plain/          <- NOT a repo, no child repos
         root_with_kids/ <- a repo that also has child repos (workspace shape)
           sub/          <- a repo
    """
    outer = _repo(str(tmp_path / "outer"))
    parent = str(tmp_path / "outer" / "parent")
    os.makedirs(parent, exist_ok=True)
    inner = _repo(os.path.join(parent, "inner"))
    plain = os.path.join(outer, "plain")
    os.makedirs(plain, exist_ok=True)
    rwk = _repo(str(tmp_path / "root_with_kids"))
    _repo(os.path.join(rwk, "sub"))
    return {"outer": outer, "parent": parent, "inner": inner,
            "plain": plain, "root_with_kids": rwk}


def _run(command, cwd):
    out = io.StringIO()
    g.main(stdin=io.StringIO(json.dumps(
        {"tool_name": "Bash", "tool_input": {"command": command}, "cwd": cwd})),
        stdout=out)
    return out.getvalue()


def _denied(payload):
    if not payload:
        return False
    return (json.loads(payload).get("hookSpecificOutput", {})
            .get("permissionDecision") == "deny")


def test_bare_commit_in_parent_of_a_repo_is_denied(tree):
    assert _denied(_run('git commit -m "x"', tree["parent"]))


def test_denial_message_names_the_child_repo(tree):
    reason = (json.loads(_run('git commit -m "x"', tree["parent"]))
              ["hookSpecificOutput"]["permissionDecisionReason"])
    assert "inner" in reason
    assert "git -C" in reason


def test_leading_absolute_cd_into_the_repo_is_allowed(tree):
    assert not _denied(_run(
        'cd %s && git commit -m "x"' % tree["inner"], tree["parent"]))


def test_git_dash_C_is_allowed(tree):
    assert not _denied(_run(
        'git -C %s commit -m "x"' % tree["inner"], tree["parent"]))


def test_commit_at_a_repo_root_is_allowed(tree):
    assert not _denied(_run('git commit -m "x"', tree["outer"]))


def test_commit_in_a_subdirectory_of_a_repo_is_allowed(tree):
    assert not _denied(_run('git commit -m "x"', tree["plain"]))


def test_repo_root_with_child_repos_is_allowed(tree):
    """The ~/Workspace row: two submodules are immediate children, but the
    toplevel IS the cwd. A condition written only against denials breaks
    every git command at the workspace root."""
    assert not _denied(_run('git commit -m "x"', tree["root_with_kids"]))


def test_read_only_git_command_is_allowed(tree):
    assert not _denied(_run("git status", tree["parent"]))


def test_non_git_command_is_ignored(tree):
    assert _run("ls -la", tree["parent"]) == ""


def test_non_bash_tool_is_ignored(tree):
    out = io.StringIO()
    g.main(stdin=io.StringIO(json.dumps({"tool_name": "Read"})), stdout=out)
    assert out.getvalue() == ""


def test_git_commit_inside_a_heredoc_is_not_judged(tree):
    cmd = "cat > f <<'EOF'\ngit commit -m \"not a real call\"\nEOF"
    assert not _denied(_run(cmd, tree["parent"]))


def test_malformed_input_fails_open_loudly(tree):
    out = io.StringIO()
    g.main(stdin=io.StringIO("not json"), stdout=out)
    payload = json.loads(out.getvalue())
    assert "additionalContext" in payload["hookSpecificOutput"]
    assert "permissionDecision" not in payload["hookSpecificOutput"]
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /home/stephaneb/Workspace && python -m pytest agent/tests/test_git_repo_guard.py -q 2>&1 | tail -5
```

Expected: collection error — `ModuleNotFoundError: No module named 'agent.git_repo_guard'`.

- [ ] **Step 3: Write the hook**

```python
#!/usr/bin/env python3
"""git_repo_guard — PreToolUse hook denying a git write issued from a
directory that contains repositories while git resolves to an outer one.

This is the configuration that produced three sessions of Hytale mislaunch:
cwd `Projects/Hytale`, toplevel `~/Workspace`, and a `Legendary/` child repo
holding everything the operator meant. A bare `git commit` there writes to
the workspace repo.

Two conditions were considered and rejected; see the design record.

A required leading `cd <abs> &&` is cwd_guard v1's withdrawn design — the
harness strips that prefix when it equals the cwd, so compliant commands
arrive stripped and get denied for lacking what was written. We therefore
check a condition, never a form.

`git check-ignore` on the cwd looked ideal and is silent for
`Projects/Android`, because tracked content under it overrides .gitignore.
A condition that goes quiet wherever someone tracked a file is a guard with
an invisible hole.

Design: see the Hytale repo,
docs/superpowers/specs/2026-10-21-hytale-git-context-structural-fix-design.md
"""

import json
import os
import re
import sys

WRITE_SUBCOMMANDS = {
    "commit", "push", "checkout", "branch", "merge", "tag", "reset",
}

HEREDOC_RE = re.compile(
    r"<<-?[ \t]*(?P<q>['\"]?)(?P<tag>[A-Za-z_][A-Za-z0-9_]*)(?P=q)")

GIT_CALL_RE = re.compile(
    r"(?:^|[;&|\n(])[ \t]*git[ \t]+(?P<rest>[^;&|\n]*)")

LEADING_CD_RE = re.compile(
    r"^cd\s+(?P<path>'[^']*'|\"[^\"]*\"|[^\s;&|]+)[ \t]*(?:&&|\|\||;|\r?\n)")


def mask_heredocs(command):
    """Blank heredoc bodies so fixture text cannot be read as a git call.
    Mis-detection only ever MASKS text, so the error direction is fail-open."""
    out, pos = [], 0
    for m in HEREDOC_RE.finditer(command):
        tag = m.group("tag")
        body_start = command.find("\n", m.end())
        if body_start == -1:
            break
        end = command.find("\n%s" % tag, body_start)
        stop = len(command) if end == -1 else end + len(tag) + 1
        out.append(command[pos:body_start])
        pos = stop
    out.append(command[pos:])
    return "".join(out)


def unquote(path):
    if len(path) >= 2 and path[0] == path[-1] and path[0] in "'\"":
        return path[1:-1]
    return path


def find_repo_root(cwd):
    """Nearest ancestor containing .git; `cwd` itself if none."""
    path = os.path.abspath(cwd)
    while True:
        if os.path.exists(os.path.join(path, ".git")):
            return path
        parent = os.path.dirname(path)
        if parent == path:
            return os.path.abspath(cwd)
        path = parent


def child_repos(cwd):
    """Sorted names of immediate children that are repository roots."""
    try:
        entries = os.listdir(cwd)
    except OSError:
        return []
    return sorted(
        e for e in entries
        if os.path.exists(os.path.join(cwd, e, ".git")))


def effective_cwd(command, cwd):
    m = LEADING_CD_RE.match(command.strip())
    if not m:
        return cwd
    target = unquote(m.group("path"))
    if os.path.isabs(target) and os.path.isdir(target):
        return target
    candidate = os.path.join(cwd, target)
    return candidate if os.path.isdir(candidate) else cwd


def is_pinned(command):
    """True when any git invocation pins its repo with -C."""
    for m in GIT_CALL_RE.finditer(mask_heredocs(command)):
        if re.match(r"-C[ \t=]", m.group("rest")):
            return True
    return False


def git_write_subcommands(command):
    found = []
    for m in GIT_CALL_RE.finditer(mask_heredocs(command)):
        tokens = [t for t in m.group("rest").split() if not t.startswith("-")]
        if tokens and tokens[0] in WRITE_SUBCOMMANDS:
            found.append(tokens[0])
    return found


def verdict(command, cwd):
    """Child repo names when this call must be denied, else None."""
    if is_pinned(command):
        return None
    if not git_write_subcommands(command):
        return None
    target = effective_cwd(command, cwd)
    if find_repo_root(target) == os.path.abspath(target):
        return None
    kids = child_repos(target)
    return kids or None


def build_denial(kids, cwd, subcommands):
    root = find_repo_root(cwd)
    lines = [
        "git_repo_guard: `git %s` here would write to the WRONG repository."
        % ", ".join(sorted(set(subcommands))),
        "cwd is: %s" % cwd,
        "git resolves that to: %s" % root,
        "but these directories below it are repositories: %s"
        % ", ".join("`%s`" % k for k in kids),
        "Name the repository explicitly: git -C <absolute-repo-path> ...",
    ]
    return {"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": "\n".join(lines),
    }}


def build_error_advisory(detail):
    """Fail-open, loudly: a guard that fails green retires the attention that
    would otherwise notice it stopped."""
    return {"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "additionalContext": (
            "git_repo_guard errored and is NOT enforcing on this call: %s"
            % detail),
    }}


def main(stdin=None, stdout=None):
    stdin = stdin if stdin is not None else sys.stdin
    stdout = stdout if stdout is not None else sys.stdout
    try:
        data = json.loads(stdin.read())
        if data.get("tool_name") != "Bash":
            return 0
        command = (data.get("tool_input") or {}).get("command") or ""
        if not command:
            return 0
        cwd = data.get("cwd")
        if not cwd:
            stdout.write(json.dumps(build_error_advisory(
                "no cwd in hook input; cannot resolve the repository")))
            return 0
        kids = verdict(command, cwd)
        if kids is None:
            return 0
        target = effective_cwd(command, cwd)
        stdout.write(json.dumps(build_denial(
            kids, target, git_write_subcommands(command))))
        return 0
    except Exception as exc:  # noqa: BLE001 — fail open on anything
        stdout.write(json.dumps(build_error_advisory(
            "%s: %s" % (type(exc).__name__, exc))))
        return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /home/stephaneb/Workspace && python -m pytest agent/tests/test_git_repo_guard.py -q 2>&1 | tail -5
```

Expected: 12 passed.

- [ ] **Step 5: Mutation-test the condition, because green on first write proves nothing**

```bash
cd /home/stephaneb/Workspace && cp agent/git_repo_guard.py /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/grg.bak && python - <<'PY'
import pathlib
p = pathlib.Path("agent/git_repo_guard.py")
s = p.read_text()
s = s.replace("    kids = child_repos(target)\n    return kids or None",
              "    return child_repos(target) or ['forced']")
p.write_text(s)
print("mutated: denial no longer requires a real child repo")
PY
python -m pytest agent/tests/test_git_repo_guard.py -q 2>&1 | tail -4```

Expected: **FAILURES**, specifically `test_commit_at_a_repo_root_is_allowed` and
`test_repo_root_with_child_repos_is_allowed`. If the suite still passes, the tests are not
exercising the condition and the guard is not believed — stop and fix the tests, not the mutation.

- [ ] **Step 6: Restore the unmutated hook and confirm green again**

```bash
cd /home/stephaneb/Workspace && cp /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/grg.bak agent/git_repo_guard.py && python -m pytest agent/tests/test_git_repo_guard.py -q 2>&1 | tail -3
```

Expected: 12 passed.

- [ ] **Step 7: Prove the real-world case before wiring anything**

```bash
cd /home/stephaneb/Workspace && printf '%s' '{"tool_name":"Bash","tool_input":{"command":"git commit -m \"x\""},"cwd":"/home/stephaneb/Workspace/Projects"}' | python agent/git_repo_guard.py
```

Expected: a `deny` payload naming `PermitKit`. This is the live shape the guard exists for — `Projects/` resolves to the workspace repo and holds a child repository.

- [ ] **Step 8: Prove the workspace root is NOT denied**

```bash
cd /home/stephaneb/Workspace && printf '%s' '{"tool_name":"Bash","tool_input":{"command":"git commit -m \"x\""},"cwd":"/home/stephaneb/Workspace"}' | python agent/git_repo_guard.py; echo "[empty output above = allowed]"
```

Expected: no output. Two submodules are immediate children here, so this is the false-positive the condition was designed around.

- [ ] **Step 9: Wire the hook into settings.json**

Add to the existing `PreToolUse` array in `~/.claude/settings.json`, as a sibling of the `cwd_guard` entry — **not** replacing it; the two guards check different things and `cwd_guard` cannot see a bare `git commit`:

```json
{
  "matcher": "Bash",
  "hooks": [
    {
      "type": "command",
      "command": "python3 /home/stephaneb/Workspace/agent/git_repo_guard.py",
      "timeout": 5
    }
  ]
}
```

- [ ] **Step 10: Validate the JSON before trusting the edit**

```bash
python3 -c "import json;d=json.load(open('/home/stephaneb/.claude/settings.json'));h=d['hooks']['PreToolUse'];print('PreToolUse entries:',len(h));print([x['hooks'][0]['command'].split('/')[-1] for x in h])"
```

Expected: the list includes both `cwd_guard.py` and `git_repo_guard.py`. A malformed `settings.json` disables **every** hook silently, which is why this is a step and not an assumption.

- [ ] **Step 11: Commit the guard**

```bash
cd /home/stephaneb/Workspace && git commit --only agent/git_repo_guard.py agent/tests/test_git_repo_guard.py -m "$(cat <<'MSG'
feat(agent): deny a git write aimed at the wrong repository

Denies a git write subcommand when the effective cwd resolves to an outer
repository while containing child repositories of its own -- the exact
configuration that made three Hytale sessions commit-capable against the
workspace repo.

Checks a condition, not a command form: requiring a leading `cd <abs> &&` is
cwd_guard v1's withdrawn design, since the harness strips that prefix when it
equals the cwd. `git check-ignore` was rejected too -- it is silent wherever
tracked content overrides .gitignore, as under Projects/Android.

Mutation-tested: forcing the denial branch breaks both repo-root allow cases,
including the ~/Workspace row where two submodules are immediate children.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
MSG
)"
```

`settings.json` is outside the repo and is not committed by this step.

---

## Task 6: Full verification and merge

**Files:** none modified. This task only reads.

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: `main` carrying the change, and a verified `Hytale: API drift` row.

- [ ] **Step 1: Full build, bare, redirected**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew build > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-build.log 2>&1
```

- [ ] **Step 2: Read the three verdict lines by name**

```bash
grep -E 'DOCS_VERDICT|BUILD SUCCESSFUL|BUILD FAILED' /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-build.log
```

Expected `DOCS_VERDICT: GREEN` and `BUILD SUCCESSFUL`. An absent verdict line is a failure.

- [ ] **Step 3: Census and coverage**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && python3 scripts/test-census.py > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-census.log 2>&1
```

- [ ] **Step 4: Coverage census**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && python3 scripts/coverage-census.py > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-cov.log 2>&1
```

- [ ] **Step 5: Read both verdicts**

```bash
grep CENSUS_VERDICT /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-census.log; grep COVERAGE_VERDICT /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-cov.log```

Expected `CENSUS_VERDICT: GREEN | 385 tests` and `COVERAGE_VERDICT: GREEN | 4/4 modules | 0 exempt`.
**Read `0 exempt`.** Anything else means `mod/hytale` compiled nothing and the build is green for the
wrong reason.

- [ ] **Step 6: Workspace health check**

```bash
cd /home/stephaneb/Workspace && python agent/health_check.py > /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-health.log 2>&1
```

- [ ] **Step 7: Read the Hytale rows and the summary**

```bash
grep -E 'Hytale|^Summary' /tmp/claude-1000/-home-stephaneb-Workspace-Projects-Hytale/74d05f43-060e-45c5-8395-d6d296811d40/scratchpad/t6-health.log
```

Expected `Hytale: API drift .... PASS`, `Session Doc: Hytale .... PASS`, and a summary with no new
failures. The pre-change baseline was 35 passed / 1 warning (`MCP currency: jcodemunch`).

- [ ] **Step 8: Agent suite**

```bash
cd /home/stephaneb/Workspace && python -m pytest agent/tests/ -q 2>&1 | tail -3
```

Expected: 600 passed (588 pre-existing + 12 new).

- [ ] **Step 9: Push the branch and open the PR**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && git push -u origin fix/git-context-structural && gh pr create --base main --title "fix: the repository is the launch directory, so the mislaunch cannot recur" --body "$(cat <<'BODY'
## What

`Projects/Hytale/Legendary` is now `Projects/Hytale`. The repository root and the
directory the operator's shell history actually launches from are the same path.

## Why a fourth fix, and why this one is different

Three guards were written on 2026-10-21, each improving detection. Sessions 19, 20
and 21 all began from the wrong directory anyway, because every guard is read
*after* the wrong command has already run — and the wrong command lives in fish
history, recalled indefinitely by up-arrow, where no document can reach it.

Detection was never the gap: the `git rev-parse` check worked at session 21's start
and reported the mislaunch correctly. Its only remedy was "restart the session",
which costs the whole session, so the session continued mislaunched.

## Also in this PR

The launch blockquote is **deleted** rather than reworded. Its condition no longer
exists, and a guard that cannot fire is read as a live hazard by the next session.

## Verification

- `DOCS_VERDICT: GREEN`
- `CENSUS_VERDICT: GREEN | 385 tests`
- `COVERAGE_VERDICT: GREEN | 4/4 modules | 0 exempt` — `0 exempt` confirms
  `mod/hytale` compiled against the real game jar through the moved path
- `agent/health_check.py`: `Hytale: API drift .... PASS`, verified broken first

Design: `docs/superpowers/specs/2026-10-21-hytale-git-context-structural-fix-design.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

- [ ] **Step 10: Check out `main` BEFORE merging**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && git checkout main
```

Not optional and not advice. Run with the PR's own branch checked out, the merge
succeeds but `--delete-branch` silently fails, so the remote branch survives and
nothing reports it. 36 for 36 since this became a step.

- [ ] **Step 11: Merge with a merge commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && gh pr merge --merge --delete-branch && git pull
```

`--merge`, never `--squash` or `--rebase`: squash collapses deliberate commit
separation, and rebase rewrites SHAs cited across the knowledge vault.

- [ ] **Step 12: Final confirmation that the defect is gone**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale && echo "toplevel: $(git rev-parse --show-toplevel)" && echo "expected: /home/stephaneb/Workspace/Projects/Hytale" && git log --oneline -1
```

This is the acceptance test for the whole change: the command that reported the
defect at session 21's start now returns the repository root.

---

## Self-Review

**Spec coverage.** Every section of the design maps to a task: the stash bundle and
the four deletions → Task 1; the in-place move and its verified preconditions →
Task 2; the live internal references and the retired launch section → Task 3; the
six live workspace references and the stale graph → Task 4; the guard, its rejected
alternatives and its mutation test → Task 5; the four verification criteria and the
PR discipline → Task 6. The design's "what this does not do" list is honoured: no
historical path is rewritten, `cwd_guard` stays in shadow mode, Shell Invocation
Discipline rule 1 is untouched.

**Placeholder scan.** No TBD, TODO, "handle edge cases", or "similar to Task N". Every
code step carries the actual code; every verification step carries the actual command
and its expected output.

**Type consistency.** The seven functions named in Task 5's Interfaces block —
`find_repo_root`, `child_repos`, `effective_cwd`, `is_pinned`,
`git_write_subcommands`, `verdict`, `main` — are the seven defined in Step 3 and the
ones the Step 1 tests call. `main(stdin, stdout)` matches `cwd_guard.main`'s
signature, which is what makes the tests injectable.

**One known gap, recorded rather than closed.** Task 5's test count is asserted as 12
while the Interfaces block lists 11 named cases; the twelfth is
`test_denial_message_names_the_child_repo`. The count in Step 4 is the authority.
