# 0007. GitHub Spec Kit for the agentic lifecycle, with agent-agnostic skills

- Status: accepted
- Date: 2026-11-13

## Context

The project is developed by coding agents (Claude Code, Codex, Bob, omp, opencode, pi,
Copilot), with the maintainer approving at defined gates. It needs a repeatable
spec → plan → tasks → implement flow. Project skills should be agent-agnostic, in the
`.agents/skills/` convention, rather than tied to one agent.

Two lifecycle tools were evaluated:

- **GitHub Spec Kit** 1.4.0: MIT licence, about 150k stars, integrations for all the
  agents above, plain markdown files in the repository.
- **Spektacular** 0.31.0: Apache-2.0, 25 stars, created February 2026, mostly a single
  maintainer, breaking changes without deprecation, and stores reached through its own
  CLI.
  - Its strengths are enforced state-machine steps, a categorised knowledge base,
    per-criterion verification and epics.
  - It supports claude, bob, codex and omp only.

Checked on 2026-11-13 with specify-cli 1.4.0 and Claude Code 2.1.319:

- Spec Kit's **Codex** integration writes skills to `.agents/skills/speckit-*/SKILL.md`.
  Its Claude integration writes to `.claude/skills/`. The two sets differ only by `$`
  instead of `/` in the invocation examples, and by the Claude-only frontmatter
  (`argument-hint`, `user-invocable`).
- Claude Code does not discover `.agents/skills`; it reads `.claude/skills`.
- Claude Code reads `AGENTS.md` natively, through the built-in `cc-plugin-agents-md`
  plugin, when the project has no `CLAUDE.md`.

## Decision

- Adopt **Spec Kit** (`specify-cli` 1.4.0) with its **Codex integration**. Skills live
  only in `.agents/skills/`.
- Make Claude Code use the same skills through a **relative symlink**,
  `.claude/skills -> ../.agents/skills`. This mirrors the maintainer's global
  `~/.claude/skills -> ~/.agents/skills`.
- **No `CLAUDE.md`.** `AGENTS.md` is the single instruction file for every agent.
- Repository layout:
  - `.specify/memory/constitution.md`: binding principles.
  - `specs/NNN-feature/`: spec, plan, research, tasks.
  - `docs/adr/`: decisions; Spec Kit has none.
  - `docs/architecture.md`: current or target design.
  - `ROADMAP.md`: feature backlog; Spec Kit has none.
  - `AGENTS.md`: operational guidance.
  - `.agents/skills/`: project skills (managed by `specify`).

## Alternatives considered

- **Spektacular.** Rejected: it is young, has one maintainer, has unstable interfaces,
  supports fewer of the agents in use, and keeps its artifacts behind its own CLI.
- **The Claude integration**, writing to `.claude/skills`. Rejected because it ties the
  skills to one agent.
- **Installing the Claude and Codex integrations together.** Rejected: it duplicates
  every skill, and the copies can drift apart.
- **Loading `.agents/` as a Claude Code plugin** (`--plugin-dir` or
  `CLAUDE_CODE_PLUGIN_DIRS`). This works, but it needs a manifest, an environment setup
  and namespaced commands (`/morning:speckit-plan`). The symlink is simpler.

## Consequences

- Every artifact is a plain file, so leaving Spec Kit costs almost nothing.
- The steps are guided, not enforced. The human gates and `speckit-analyze` compensate.
- Skill text says `$speckit-…` (Codex syntax). In Claude Code the skills are
  `/speckit-…`. AGENTS.md explains this.
- Symlinks need `core.symlinks=true` in git, the default on Linux and macOS. Windows
  checkouts may need extra setup.
- `specify integration switch` (specify-cli 1.4.0) drops the execute bit from
  `.specify/scripts/bash/*.sh`, even though it reports setting it. Reproduced in a clean
  repository. Restore it with `chmod +x` after a switch.
- Spektacular's knowledge categories (conventions, glossary, architecture, gotchas,
  learnings, decisions) are a useful model. Here they map to the constitution and
  AGENTS.md, `docs/architecture.md`, and the ADRs.
