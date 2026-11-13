# Contract: `--format json` output for `prs`

A stable, versioned shape for scripting and for feature 004 (summary). Additive changes
only; breaking changes bump `schema`.

```json
{
  "schema": "morning.my-prs/1",
  "section": "My PRs",
  "generatedAt": "2026-11-13T07:30:00Z",
  "prs": [
    {
      "repo": "quarkusio/quarkus",
      "number": 48427,
      "title": "…",
      "url": "https://github.com/quarkusio/quarkus/pull/48427",
      "draft": false,
      "group": "CHANGES_REQUESTED",
      "reviewDecision": "CHANGES_REQUESTED",
      "mergeable": "UNKNOWN",
      "ci": "FAILURE",
      "failures": [
        { "check": "JVM Tests - JDK 21", "url": "…",
          "class": "UNRELATED_FAILS_ON_BASE", "evidence": "failed on base 1a2b3c4 (2026-11-12)" }
      ],
      "actions": ["ADDRESS_REVIEW"],
      "updatedAt": "2026-10-01T12:00:00Z"
    }
  ],
  "apiRequests": 2
}
```
