# 0003. Deterministic first; the LLM is isolated, credential-free and read-only

- Status: accepted
- Date: 2026-11-13

## Context

The original routine was a Claude skill. The LLM did everything, including calling the
GitHub API with the user's credentials. That was non-deterministic, expensive in tokens,
and gave the model both credentials and write access to public services.

## Decision

- The program does all fetching, filtering, diffing, scoring and rendering.
- An LLM is invoked only for judgement steps:
  - root cause of an unmatched CI failure;
  - whether a PR's CI failure relates to the change;
  - the final summary;
  - an optional review explanation.
- Each invocation is an **independent, single-purpose call with structured output**.
- The LLM never receives credentials. It gets data fetched by the program, plus read-only
  tools (`@Tool` methods) that go through a `ReadOnlyGitHubGateway`. The gateway enforces
  a repository allowlist (the analysed section's `repo` plus its configured
  `related-repos`; see ADR-0008), an allowlist of IDs from the current run, output caps,
  call budgets and redaction.
- The program contains **no code path that writes to external services**.
- Results are cached by content hash. `--no-llm` always works.

## Alternatives considered

- **Prefetch everything, give the LLM no tools.** Cheaper and more predictable, but root
  cause analysis is shallower. Rejected by the maintainer in favour of allowlisted read
  tools.
- **Keep the agent skill.** Rejected because of token cost, non-determinism and credential
  exposure.

## Consequences

- A prompt injection through CI logs or PR text can, at worst, produce a wrong verdict.
  Verdicts are shown with their confidence level.
- Two ArchUnit tests and a GraphQL operation check enforce the read-only boundary
  (constitution Principle III).
