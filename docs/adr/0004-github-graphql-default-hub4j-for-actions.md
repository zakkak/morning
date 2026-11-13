# 0004. GitHub access: GraphQL by default, hub4j only where GraphQL has no coverage

- Status: accepted
- Date: 2026-11-13

## Context

No maintained Java GitHub library covers both REST and GraphQL:

- hub4j (`org.kohsuke:github-api`) uses GraphQL only internally;
  `GitHub.createGraphQLRequest` is package-private.
- The alternatives are dead (Eclipse EGit GitHub) or a poor fit (Apollo targets
  Kotlin/Android, and Netflix DGS codegen is heavy for a few queries).

What GitHub's GraphQL schema lacks, checked by introspection on 2026-11-13:

- **Actions job logs**: no type exposes them.
- **Listing workflows**: `Repository` has no `workflows` connection.
- **Filtering runs**: `Workflow.runs` takes no branch, event, status or date filter, and
  `WorkflowRun` has no `conclusion`, `headBranch` or `headRepository`.
- **PR diffs**: `PullRequestChangedFile` has no patch.

What hub4j 1.330 and 2.0-rc.7 lack:

- the Issue Timeline API (`cross-referenced` events);
- the combined commit status;
- `reviewDecision`;
- a public way to send raw requests.

hub4j getters also load lazily and can trigger hidden requests.

## Decision

**Use GraphQL by default, and hub4j only for what GraphQL does not expose.**

- **`quarkus-smallrye-graphql-client` (`DynamicGraphQLClient`)** for PRs, issues,
  reviews (`reviewDecision`), checks and statuses (`statusCheckRollup`), timelines,
  file contents (`object(expression:)`) and search.
  - Queries are named documents in `src/main/resources/graphql/*.graphql`, wrapped by a
    `GraphQLQueries` bean that rejects mutations and subscriptions.
  - A test validates every document against a vendored GitHub schema.
- **hub4j 1.330**, through `io.quarkiverse.githubapi:quarkus-github-api`, only for:
  - Actions: workflow runs, jobs, job logs;
  - PR diffs (`listFiles()` → `getPatch()`).

## Alternatives considered

- **hub4j wherever REST can do it.** Rejected:
  - about 4 calls per PR for `morning prs`, against 1–2 GraphQL queries;
  - only an approximate review state;
  - hand-merging check runs and statuses;
  - no timeline support;
  - hidden lazy calls.
- **GraphQL only.** Impossible because of the gaps listed above.
- **A hand-written REST client.** Rejected: it reinvents what hub4j already provides.

## Consequences

- There are two clients, each locked down separately (constitution Principle III).
- GraphQL queries are not compile-time checked; the schema-validation test mitigates
  this.
- hub4j 1.330 (September 2025) is older than 6 months, because 2.0 is still a release
  candidate. The repository is active, so this is accepted as an exception under
  constitution Principle VI. Migrate to 2.0 at GA. hub4j types are confined to the Actions
  and diff fetchers to keep that migration local.
- Phase 0 must verify that `quarkus-github-api` 1.330.0 works with Quarkus 3.40.1. If it
  doesn't, depend on `org.kohsuke:github-api:1.330` directly.
