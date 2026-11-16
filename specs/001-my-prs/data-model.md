# Data Model: 001-my-prs

All types are immutable Java records. Packages are relative to `net.zakkak.morning`.

## Configuration (`config`)

```
MorningConfig
  sections: List<Section>            # at least 1; names unique
  llm: LlmSettings                   # optional

sealed interface Section permits MyPrsSection, CiStatusSection,
                                 ReviewCandidatesSection, SummarySection
  type(): String                     # Jackson discriminator "type"
  name(): String                     # optional in YAML; default derived (see below)

MyPrsSection(name, includeOrgs: List<String>, excludeOrgs: List<String>)
  - include-orgs and exclude-orgs must not overlap
  - each org matches ^[A-Za-z0-9-]{1,39}$
  - default name: "my-prs"

SummarySection(name, order: List<SummaryCategory>)     # parsed now, used in 004
CiStatusSection(...), ReviewCandidatesSection(...)     # parsed now, rejected at run
                                                       # time: "not supported yet (00N)"

LlmSettings(enabled: boolean = true, maxCalls: int = 15)   # parsed now, used from 002
```

Validation runs at load time, before any API call:
- Unknown property, missing required field, wrong type, or duplicate section name gives a
  `ConfigError(path, message)`, where `path` is like `morning.sections[1].include-orgs`.
- Any string value matching a token pattern gives a `ConfigError(path, "looks like a
  secret; secrets must come from the environment")`. The value is never printed.

## GitHub (`github`)

```
GitHubCredentials(token)             # toString() returns "GitHubCredentials[***]"
```

The records below are mapped from `my-prs.graphql`, `pr-checks-page.graphql` and
`base-and-attempts.graphql`. Field names follow GitHub's GraphQL schema.

```
PullRequestNode(id, number, title, url, isDraft, mergeable: Mergeable,
                reviewDecision: ReviewDecision?, updatedAt: Instant,
                repo: String /* owner/name */, repoId, baseRefName, baseRefId, headRefOid,
                rollupState: RollupState?, checks: List<CheckContext>,
                checksHasNextPage: boolean, checksEndCursor: String?)

CheckContext(name, kind: CHECK_RUN | STATUS_CONTEXT,
             outcome: SUCCESS | FAILURE | CANCELLED | PENDING | NEUTRAL | SKIPPED,
             url: String?, app: String?)
  # CheckRun.conclusion: FAILURE, TIMED_OUT, STARTUP_FAILURE, ACTION_REQUIRED → FAILURE
  # CheckRun.conclusion: CANCELLED → CANCELLED (usually fail-fast or superseded; not a failure)
  # StatusContext.state: FAILURE, ERROR → FAILURE
  # Absent conclusion with status != COMPLETED → PENDING

BaseHistory(refId, commits: List<BaseCommit>)                   # newest first; keyed by Ref node ID
BaseCommit(oid, committedDate, checks: List<CheckContext>)

CheckAttempts(prId, attemptsByName: Map<String, List<Outcome>>) # all attempts on head SHA

PeerPr(id, number, baseRefName, headRefOid, checks: List<CheckContext>)  # another open PR
Peers(prs: List<PeerPr>, mine: boolean)
  # mine = true: my other open PRs in the same repo and base (rule A); needs ≥ 1 failure
  # mine = false: others' recent open PRs, when I have none there (rule C); needs ≥ 2
```

`Mergeable` is MERGEABLE, CONFLICTING or UNKNOWN. `ReviewDecision` is APPROVED,
CHANGES_REQUESTED, REVIEW_REQUIRED, or null when there is no review policy.

## Analysis (`prs`)

```
FailureClass = UNRELATED_FAILS_ON_BASE | UNRELATED_FAILS_ON_OTHER_PRS | LIKELY_FLAKY | NOT_ANALYSED
  # rules apply in this order; the first match wins
  # UNRELATED_FAILS_ON_OTHER_PRS: among peers on the same base with a different head SHA,
  #   failures ≥ the minimum and failures ≥ passes (pending/cancelled/skipped don't count)

CheckFailure(check: CheckContext, failureClass: FailureClass,
             evidence: String?)     # e.g. "failed on base 1a2b3c4 (2026-11-12)"

PrGroup = CHANGES_REQUESTED (1) | CI_FAILURE (2) | OTHER (3)
  # CHANGES_REQUESTED: reviewDecision == CHANGES_REQUESTED
  # CI_FAILURE: any CheckFailure whose class is not UNRELATED_* 
  #   (LIKELY_FLAKY still counts: I need to rerun it)
  # OTHER: everything else

Action = ADDRESS_REVIEW | FIX_CI | RERUN_FLAKY | RESOLVE_CONFLICT | WAIT_FOR_CI
       | AWAITING_REVIEW | READY_TO_MERGE | DRAFT
  # derived in this order; several may apply, all are shown

PullRequestStatus(pr: PullRequestNode, group: PrGroup,
                  failures: List<CheckFailure>, actions: List<Action>)

MyPrsReport(section: String, generatedAt: Instant,
            prs: List<PullRequestStatus>,        # sorted: group asc, updatedAt asc
            apiRequests: int,                    # for SC-001
            warnings: List<String>)              # e.g. "showing 50 of 63 open PRs"
```

## State transitions

None. Each run is a stateless fetch, analyse and render. Caching arrives with feature 002.
