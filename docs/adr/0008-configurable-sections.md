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
- **Format and loading.** YAML, read with Jackson (`jackson-dataformat-yaml`, managed by
  the Quarkus BOM) into Java records.
  - Sections are polymorphic through Jackson's `@JsonTypeInfo(property = "type")` and
    `@JsonSubTypes`.
  - Unknown properties are rejected.
  - Constraints are checked with Bean Validation (`quarkus-hibernate-validator`).
  - Errors carry the field path (for example `morning.sections[1].repo`).
  - The user's routine is kept separate from the app's own `application.properties`.
  - Default location: `~/.config/morning/config.yaml`; `--config <path>` overrides it.
    `--config` is a plain Picocli option and the file is read by our own loader, not by
    SmallRye Config. This is what Quarkus's own CLI does: `RegistryClientMixin` declares
    `--config`, and `RegistriesConfigMapperHelper` reads `~/.quarkus/config.yaml` with
    Jackson YAML.
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
- **`quarkus-config-yaml` with a typed `@ConfigMapping`** (this ADR's first draft).
  Rejected during planning of 001 (research R2):
  - SmallRye config mappings have no type-discriminated polymorphism, so a list of
    differently-typed sections would need one superset group plus hand-written per-type
    validation.
  - Quarkus resolves config at startup, before Picocli parses `--config`.
- **`quarkus.config.locations`** (SmallRye extra config sources). Rejected: it must be
  set as a system property or environment variable before startup, so `--config` would
  need a pre-parsing `main`, and it still maps through `@ConfigMapping` (no polymorphism).
- **Picocli `@file` argument files or `PropertiesDefaultProvider`.** Rejected as the
  routine format: both are flat lists of options and can't express typed, nested
  sections. `@file` stays available (Picocli enables it by default) for saving common
  flags such as `--width`.
- **A custom config parser or file format.** Rejected: Jackson and Bean Validation
  already provide typed, validated YAML.

## Consequences

- Specs are written against generic section types. The maintainer's example config is
  the reference for acceptance tests and success criteria.
- The cache and the "last successful run" state are keyed per section.
- A config error is reported with the section name and field, before any API call.
