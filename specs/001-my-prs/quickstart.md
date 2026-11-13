# Quickstart / validation: 001-my-prs

## Prerequisites

- JDK 25. Maven is not needed, because the project ships the `./mvnw` wrapper.
- For live runs only: a GitHub token in `GITHUB_TOKEN` or `GH_TOKEN`, or a `gh auth login`.

## Automated validation (no network)

```bash
./mvnw verify
```

This must pass, and it covers:

- spec 001 US1 scenarios 1–5 and US3 scenarios 1–4, against WireMock fixtures;
- ArchUnit rules: GraphQL client confinement, the hub4j confinement placeholder, no
  mutation documents;
- GraphQL schema validation of `src/main/resources/graphql/*.graphql`;
- SC-001: the fixture run asserts `apiRequests <= 3`;
- SC-003: the token sentinel value never appears in stdout, stderr or logs.

## Manual validation (live, read-only)

```bash
mkdir -p ~/.config/morning && cp examples/zakkak.yaml ~/.config/morning/config.yaml
java -jar target/quarkus-app/quarkus-run.jar prs                  # tty table
java -jar target/quarkus-app/quarkus-run.jar prs --format md      # markdown
java -jar target/quarkus-app/quarkus-run.jar prs --format json | jq '.apiRequests'   # expect <= 3
java -jar target/quarkus-app/quarkus-run.jar prs --width 60       # rows wrap, nothing truncated
java -jar target/quarkus-app/quarkus-run.jar --config /nonexistent prs; echo $?   # expect 2
```

Expected:

- PRs with changes requested come first.
- PRs with failing checks that don't also fail on base come second.
- Within each group, the oldest update comes first.
