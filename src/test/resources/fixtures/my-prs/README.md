Hand-written GraphQL responses in the shape recorded from GitHub's live API on 2026-11-16
(documents in `src/main/resources/graphql/`). Owners are fictional (`example-org`,
`other-org`) so that every spec scenario is covered deterministically.

| PR | Scenario | Expected group |
|---|---|---|
| #1 | changes requested | CHANGES_REQUESTED |
| #2 | `build` fails; the newest base commit with `build` failed it too (a newer one has no checks) | OTHER (unrelated) |
| #3 | `test` fails; passed on the newest base commit (failed on an older one) | CI_FAILURE |
| #4 | `it` fails; an earlier attempt passed | CI_FAILURE (likely flaky) |
| #5 | green, approved | OTHER (ready to merge) |
| #6 | draft, pending, mergeable UNKNOWN | OTHER |
| #7 | `lint` fails, no base counterpart but also fails on my #8 (rule A); a second page adds failing status `ci/page-two` (pending on base) | CI_FAILURE |
| #8 | `lint` also fails on my #7 (rule A); `deploy` was cancelled (rule B, not a failure) | OTHER |
| #9 | my only PR in `solo-org/delta`; `e2e` fails on 2 of 3 other people's PRs to `main` (rule C; a `release` PR and #9 itself are ignored) | OTHER |
