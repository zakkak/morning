# Contract: CLI (001 scope)

```
morning [GLOBAL OPTIONS] [COMMAND]

COMMAND
  prs        run the my-prs section(s)
  (none)     same as `all`; in 001, `all` runs the my-prs sections only and notes
             "ci, reviews, summary: not implemented yet"

GLOBAL OPTIONS
  --config=<path>         default: $XDG_CONFIG_HOME/morning/config.yaml, else
                          ~/.config/morning/config.yaml
  --section=<name>        run only the named section(s); repeatable
  --format=tty|md|json    default: tty if stdout is a terminal, else md
  --width=<n>             default: $COLUMNS, then `stty size`, then 120
  --no-llm                accepted; no effect in 001 (no LLM use yet)
  -h, --help / -V, --version
```

## Exit codes

| Code | Meaning |
|---|---|
| 0 | success; also when there are no open PRs |
| 1 | at least one section failed at run time (for example a GitHub API error); other sections still rendered |
| 2 | usage, configuration or credential error, reported before any API call |

## Streams

- stdout carries the report only.
- stderr carries diagnostics.
- No token or secret appears in either stream (tested).

## Error message formats (stderr)

```
morning: no configuration found at <path>. See examples/ in the morning repository, or pass --config.
morning: invalid configuration <path>: morning.sections[1].include-orgs: <message>
morning: GitHub token not found. Set GITHUB_TOKEN or GH_TOKEN, or log in with `gh auth login`.
morning: section "<name>": <type> is not supported yet (planned in feature <NNN>)
```
