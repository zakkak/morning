# Contract: configuration file (001 scope)

YAML, UTF-8. The top-level key is `morning`. Unknown keys are errors. The full
section-type catalogue is in `docs/architecture.md`, "Configuration".

```yaml
morning:
  sections:                     # required, at least 1
    - type: my-prs              # required discriminator
      name: My PRs              # optional, unique; default "my-prs"
      include-orgs: [quarkusio] # optional; only these owners
      exclude-orgs: [JCrete]    # optional; never these owners (overlap with include is an error)
    - type: summary             # parsed; used by feature 004
      order: [changes-requested, my-ci-failures, new-ci-failures, needs-owner, reviews]
    - type: ci-status           # parsed; at run time: not supported yet (002)
      repo: graalvm/mandrel
    - type: review-candidates   # parsed; at run time: not supported yet (003)
      repo: quarkusio/quarkus
  llm:                          # optional; used from 002
    enabled: true
    max-calls: 15
```

In 001 only `my-prs` fields are fully validated. Other types are validated for structure
only (known keys and types), so `examples/zakkak.yaml` parses unchanged.
