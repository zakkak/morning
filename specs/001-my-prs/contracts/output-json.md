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
      "cancelled": ["Native Tests - JDK 21"],
      "actions": ["ADDRESS_REVIEW"],
      "updatedAt": "2026-10-01T12:00:00Z"
    }
  ],
  "apiRequests": 2,
  "warnings": []
}
```

- `class`: `UNRELATED_FAILS_ON_BASE`, `UNRELATED_FAILS_ON_OTHER_PRS`, `LIKELY_FLAKY` or
  `NOT_ANALYSED`.
- `cancelled`: names of cancelled checks. They are not failures (research R11).
- `warnings`: non-fatal notes, for example `"showing 50 of 63 open PRs"`.
- With several `my-prs` sections, one document is printed per section, one after another
  (`jq` reads such a stream directly).
