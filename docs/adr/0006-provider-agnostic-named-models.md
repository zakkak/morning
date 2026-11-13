# 0006. Provider-agnostic LLM access through named models

- Status: accepted
- Date: 2026-11-13

## Context

The project starts with Anthropic, but users must be able to switch provider later.

## Decision

- AI services are declared with `@RegisterAiService(modelName = "fast" | "deep")`. Code
  depends only on LangChain4j abstractions.
- `fast` is for cheap classification (initially Claude Haiku). `deep` is for root cause
  analysis and the summary (initially Claude Sonnet).
- The provider and model are chosen in `application.properties`. Other providers come in
  through Maven profiles (`-Pllm-openai`, `-Pllm-ollama`) that add the extension.

## Consequences

- Switching provider means a configuration change, plus a rebuild if
  `chat-model.provider` turns out to be fixed at build time. Verify this in the first
  feature that adds LLM support.
- Moving `deep` to Opus is a cost decision, taken only if root cause analysis quality is
  poor.
