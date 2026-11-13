# morning

A deterministic CLI for a configurable daily developer routine on GitHub:

- the state of your open PRs;
- CI status of the repositories you care about, with known-issue tracking;
- PRs matching your review interests, ranked by how fast you can unblock them;
- a prioritised summary.

You describe the routine in a YAML file
([example](examples/zakkak.yaml): Mandrel CI plus Quarkus native-image reviews). An LLM is
used only where judgement is needed. It never sees credentials and cannot write
anywhere.

**Status:** specified, not implemented yet. See [ROADMAP.md](ROADMAP.md).

- Design: [docs/architecture.md](docs/architecture.md), with decisions in [docs/adr/](docs/adr/).
- Development: spec-driven with [GitHub Spec Kit](https://github.com/github/spec-kit).
  See [AGENTS.md](AGENTS.md).
