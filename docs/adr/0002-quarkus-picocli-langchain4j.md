# 0002. Java 25 with Quarkus, Picocli and quarkus-langchain4j

- Status: accepted
- Date: 2026-11-13

## Context

The morning routine needs a CLI that fetches data from GitHub, applies rules, and
occasionally calls an LLM. The maintainer works on Mandrel, GraalVM and Quarkus.

## Decision

- **Java 25** (installed JDK).
- **Quarkus 3.40.1**, the latest stable release when this was written (4.0 was at CR1).
- **`quarkus-picocli`** for the command-line interface.
- **`quarkus-langchain4j` 1.15.1**, the latest stable release (2.0 was in beta), with the
  Anthropic extension first (see ADR-0006).
- **Maven** as the build tool.

## Alternatives considered

- A plain Java CLI without Quarkus: loses CDI, config, the REST and GraphQL clients, and
  the native build.
- Another language: rejected, because the maintainer's expertise and the native-image
  dogfooding value are in Java and Quarkus.

## Consequences

- A native build with Mandrel is possible later, giving fast startup and dogfooding.
- Upgrade Quarkus 4.0 and quarkus-langchain4j 2.0 when they reach GA, in a dedicated change.
