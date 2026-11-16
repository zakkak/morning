package net.zakkak.morning.prs;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classifies a PR's failing checks with deterministic rules only (FR-003, research R4/R5). The
 * first matching rule wins:
 * <ol>
 * <li>{@link FailureClass#UNRELATED_FAILS_ON_BASE}: the most recent base commit (of the last 30)
 * that ran a check with the same name and finished it (success or failure) failed it.</li>
 * <li>{@link FailureClass#LIKELY_FLAKY}: an earlier attempt with the same name on the PR's head
 * commit succeeded.</li>
 * <li>otherwise {@link FailureClass#NOT_ANALYSED}.</li>
 * </ol>
 * A check name that is missing on base never yields "unrelated".
 */
public final class FailureClassifier {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private FailureClassifier() {
    }

    public static List<CheckFailure> classify(PullRequestNode pr, BaseHistory base, CheckAttempts attempts) {
        List<CheckFailure> failures = new ArrayList<>();
        for (CheckContext check : pr.checks()) {
            if (check.outcome() == Outcome.FAILURE) {
                failures.add(classify(check, base, attempts));
            }
        }
        return failures;
    }

    static CheckFailure classify(CheckContext check, BaseHistory base, CheckAttempts attempts) {
        Optional<BaseCommit> failedOnBase = mostRecentFinishedOnBase(check.name(), base)
                .filter(c -> outcomeOf(check.name(), c) == Outcome.FAILURE);
        if (failedOnBase.isPresent()) {
            BaseCommit c = failedOnBase.get();
            return new CheckFailure(check, FailureClass.UNRELATED_FAILS_ON_BASE,
                    "failed on base " + c.oid().substring(0, Math.min(7, c.oid().length())) + " ("
                            + DAY.format(c.committedDate()) + ")");
        }
        List<Outcome> tries = attempts.attemptsByName().getOrDefault(check.name(), List.of());
        if (tries.contains(Outcome.SUCCESS)) {
            return new CheckFailure(check, FailureClass.LIKELY_FLAKY, "passed in an earlier attempt on this commit");
        }
        return new CheckFailure(check, FailureClass.NOT_ANALYSED, null);
    }

    private static Optional<BaseCommit> mostRecentFinishedOnBase(String name, BaseHistory base) {
        return base.commits().stream().filter(c -> {
            Outcome o = outcomeOf(name, c);
            return o == Outcome.SUCCESS || o == Outcome.FAILURE;
        }).findFirst();
    }

    /** FAILURE if any same-named check failed on that commit, else SUCCESS if one passed. */
    private static Outcome outcomeOf(String name, BaseCommit commit) {
        Outcome result = null;
        for (CheckContext c : commit.checks()) {
            if (c.name().equals(name)) {
                if (c.outcome() == Outcome.FAILURE) {
                    return Outcome.FAILURE;
                }
                if (c.outcome() == Outcome.SUCCESS) {
                    result = Outcome.SUCCESS;
                }
            }
        }
        return result;
    }
}
