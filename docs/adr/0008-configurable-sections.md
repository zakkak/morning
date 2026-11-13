# 0008. A configurable routine: built-in section types, composed in YAML

- Status: accepted
- Date: 2026-11-13

## Context

The routine began as one maintainer's checklist: graalvm/mandrel CI, my PRs, and Quarkus
PRs about native-image. Hardcoding those repositories, labels, path patterns and the
Mandrel tracking-issue convention would make the tool useless to anyone else, and
awkward even for its author when interests change.

## Decision

- **Built-in section types.** The routine is a list of sections. Each section is an
  instance of one of four types:
  - `my-prs`: open PRs authored by the user, with optional org include and exclude lists.
  - `ci-status`: one repository's Actions CI, with optional branch, workflow and event
    filters and a tracking-issue strategy (ADR-0005).
  - `review-candidates`: one repository, plus the interests that make a PR relevant:
    labels, path globs, title keywords, and score weights.
  - `summary`: a configurable priority order over the other sections' findings.

  A config can hold any number of each type, for example two `ci-status` sections.
  Adding a new **type** requires Java code. There is no plugin system.
- **Tracking-issue strategies** for `ci-status`:
  - `none`.
  - `workflow-inputs`: issue numbers are read from configured keys in the workflow YAML,
    and jobs are mapped to issues by job-name prefix. This generalises Mandrel's
    `quarkus-ecosystem-issue.java` convention.

  New strategies are added in code when a real repository needs one.
- **Format and loading.** YAML, read with `quarkus-config-yaml` into a typed
  `@ConfigMapping(prefix = "morning")`. That gives validation, lists of groups, and
  environment-variable overrides with no custom parser.
  - Default location: `~/.config/morning/config.yaml`; `--config <path>` overrides it.
  - With no config, the CLI exits with an error pointing to `examples/`. There are no
    built-in default repositories.
  - The maintainer's routine ships as `examples/zakkak.yaml`.
- **Security boundaries stay fixed.** Configuration:
  - MUST NOT contain secrets;
  - can only widen **read** scope;
  - cannot enable any write.

  The LLM tool allowlist for a section is its `repo` plus its explicit `related-repos`
  (for example `oracle/graal` for Mandrel).
- **Scope:** GitHub only. Other forges or trackers would need new fetchers; they are
  roadmap items.

## Alternatives considered

- **Hardcoded routine plus a few flags.** Rejected: it doesn't serve other people's
  workflows.
- **A plugin SPI for new section types.** Rejected as premature (YAGNI). Revisit if
  several external contributors need custom types.
- **A custom config parser or file format.** Rejected: Quarkus config already provides
  typed, validated YAML.

## Consequences

- Specs are written against generic section types. The maintainer's example config is
  the reference for acceptance tests and success criteria.
- The cache and the "last successful run" state are keyed per section.
- A config error is reported with the section name and field, before any API call.
