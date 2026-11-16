# AGENTS.md

Operational guidance for coding agents working on `morning`. **The binding rules are in
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).** Read it first.
If anything here conflicts with it, the constitution wins.

## What this is

A Java/Quarkus CLI that runs a configurable daily developer routine on GitHub. The
routine is YAML-defined sections (`my-prs`, `ci-status`, `review-candidates`, `summary`;
ADR-0008). It is deterministic first, and calls an LLM only in isolated, credential-free
calls. The maintainer's routine, in `examples/zakkak.yaml`, is the reference for tests.

## Where things are

| Path | What |
|---|---|
| `.specify/memory/constitution.md` | Binding principles (read-only boundary, LLM isolation, and so on) |
| `ROADMAP.md` | Ordered features and their status |
| `specs/NNN-feature/` | Spec Kit artifacts: `spec.md`, `plan.md`, `research.md`, `tasks.md`, … |
| `docs/architecture.md` | Current and target design: components, GitHub access table, LLM boundary, CI pipeline |
| `docs/adr/` | Decisions and their reasons (append-only once the first feature is merged) |
| `examples/` | Example configurations; `zakkak.yaml` is the reference config |
| `.specify/templates/`, `.agents/skills/speckit-*` | Spec Kit templates and agent-agnostic skills (managed by `specify`, Codex integration; don't hand-edit) |

## Agent setup

Project skills live in `.agents/skills/` (ADR-0007).

- **Codex, opencode, omp, pi and other agents** read `.agents/skills` and `AGENTS.md`
  natively. Invoke skills as `$speckit-plan` (Codex) or your agent's equivalent.
- **Claude Code** reads the same skills through the symlink
  `.claude/skills -> ../.agents/skills`. Invoke them as `/speckit-plan`. Claude Code
  reads `AGENTS.md` natively, because the repo has no `CLAUDE.md`. Don't add one.
- Skill text uses Codex's `$speckit-…` syntax. Read it as your agent's own invocation
  syntax.

## Workflow (Spec Kit)

Skill names below are agent-neutral. Invoke them with your agent's syntax (see above).


1. Pick the next item from `ROADMAP.md`.
2. Specify: `speckit-specify` for new features. 001–004 are already specified.
3. Run `speckit-clarify` while any `[NEEDS CLARIFICATION]` markers remain.
4. **Gate:** the maintainer approves the spec.
5. Plan: `speckit-plan`. When planning, **also read `docs/architecture.md` and the
   relevant ADRs**. The plan template only loads the spec and the constitution.
6. **Gate:** the maintainer approves the plan.
7. `speckit-tasks`, then `speckit-analyze`.
8. Implement: `speckit-implement`, on the branch named after the spec directory (for
   example `001-my-prs`). Commit locally per task.
9. Close out:
   - update `docs/architecture.md` (the "Implemented" column);
   - update the `ROADMAP.md` status;
   - add an ADR for any cross-cutting decision;
   - add new lessons below.
10. **Gate:** the maintainer reviews before merge.

## Commands

```bash
./mvnw verify                        # build + all tests (no network)
./mvnw package -DskipTests           # just the jar
java -jar target/quarkus-app/quarkus-run.jar --config examples/zakkak.yaml prs
./mvnw quarkus:dev -Dquarkus.args="prs --no-llm"
```

Opt-in live tests (`-Dmorning.live=true`) are reserved by the constitution; none exist yet.
For live checks, run the jar (read-only) as in `specs/001-my-prs/quickstart.md`.

## Conventions

- Java 25. Use records for all data passing between fetch, analyse and render.
- GitHub data uses GraphQL by default: one named `.graphql` file per query in
  `src/main/resources/graphql/`, executed through `GraphQLQueries`. Use hub4j **only** for
  Actions runs, jobs and logs, and PR diffs (ADR-0004).
- Never build GraphQL query strings at runtime. Pass values as variables.
- LLM calls go through `@RegisterAiService(modelName = "fast"|"deep")` interfaces with
  record outputs. No provider-specific code (ADR-0006).
- Every new GitHub interaction gets a WireMock fixture. Tests must not use the network.
- No repository, label, path or person is hardcoded in `src/main`. Everything
  user-specific comes from configuration. Defaults may exist only for generic settings
  (for example score weights).

## Hard don'ts

These summarise the constitution:

- Don't add any code path that writes to GitHub or other services.
- Don't pass tokens or keys to anything except their one client. Never log them.
- Don't disable TLS verification.
- Don't push, open PRs or issues, or comment anywhere unless the maintainer explicitly
  asks. Local commits are fine.
- Commits: end with `Assisted-By: <Tool> <Model>`.

## Lessons learned

<!-- Append short, durable lessons here (gotchas, surprising behaviour), one bullet each. -->

- GitHub GraphQL has no Actions job logs, no workflow listing or run filters, and no PR
  patches. Don't try to use it for those.
- hub4j has no Issue Timeline API and no combined-status call, and some of its getters
  trigger lazy extra requests.
- `specify integration switch` (specify-cli 1.4.0) drops the execute bit from
  `.specify/scripts/bash/*.sh`, even though it reports setting it. Restore it with
  `chmod +x`.
- `@QuarkusMainTest` does not inject test resources (for example `@ConnectWireMock`),
  and a `QuarkusTestResourceLifecycleManager`'s statics live in a different class loader
  from the test's. Start WireMock in the test and pass its URL as a system property,
  which every launch reads (see `PrsCommandTest`).
- The generated pom skips `*IT` classes (`skipITs=true`, failsafe). Name `@QuarkusMainTest`
  classes `*Test` so `./mvnw verify` runs them. The JUnit artifact is `quarkus-junit`.
- Surefire sets `GITHUB_TOKEN` to a sentinel, so tests can never use a real token and
  SC-003 tests can look for it in all output.
- Base branches often don't run the same checks as PRs (oracle/graal `master` ran 3 check
  names in 30 commits). In a live run on 2026-11-16, none of 33 failing checks had a base
  counterpart, so "fails on base" rarely fires; that is what US2's LLM step is for.
- Several `org:` qualifiers in one GitHub search are ORed, `-org:` qualifiers stack, and
  `org:` also matches personal accounts.
