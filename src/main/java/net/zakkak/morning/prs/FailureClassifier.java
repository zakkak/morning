package net.zakkak.morning.prs;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Classifies a PR's failing checks with deterministic rules only (FR-003, research R4, R5, R11).
 * The first matching rule wins:
 * <ol>
 * <li>{@link FailureClass#UNRELATED_FAILS_ON_BASE}: the most recent base commit (of the last 30)
 * that ran a check with the same name and finished it (success or failure) failed it.</li>
 * <li>{@link FailureClass#UNRELATED_FAILS_ON_OTHER_PRS}: among other open PRs to the same base
 * with a different head commit, the check failed on at least {@link Peers#minFailures()} and on
 * at least as many as it passed on. Peers are my own PRs (rule A) or, when I have none there,
 * other people's (rule C).</li>
 * <li>{@link FailureClass#LIKELY_FLAKY}: an earlier attempt with the same name on the PR's head
 * commit succeeded.</li>
 * <li>otherwise {@link FailureClass#NOT_ANALYSED}.</li>
 * </ol>
 * Only SUCCESS and FAILURE count as evidence; pending, cancelled and skipped checks are ignored.
 * A check name that is missing everywhere never yields "unrelated".
 */
public final class FailureClassifier {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final int LISTED = 3;

    private FailureClassifier() {
    }

    public static List<CheckFailure> classify(PullRequestNode pr, BaseHistory base, CheckAttempts attempts,
            Peers peers) {
        List<PeerPr> comparable = peers.prs().stream()
                .filter(p -> !p.id().equals(pr.id()))
                .filter(p -> p.baseRefName() != null && p.baseRefName().equals(pr.baseRefName()))
                .filter(p -> p.headRefOid() == null || !p.headRefOid().equals(pr.headRefOid()))
                .toList();
        Peers relevant = new Peers(comparable, peers.mine());
        List<CheckFailure> failures = new ArrayList<>();
        for (CheckContext check : pr.checks()) {
            if (check.outcome() == Outcome.FAILURE) {
                failures.add(classify(check, base, attempts, relevant));
            }
        }
        return failures;
    }

    static CheckFailure classify(CheckContext check, BaseHistory base, CheckAttempts attempts, Peers peers) {
        Optional<BaseCommit> failedOnBase = mostRecentFinishedOnBase(check.name(), base)
                .filter(c -> outcomeOf(check.name(), c.checks()) == Outcome.FAILURE);
        if (failedOnBase.isPresent()) {
            BaseCommit c = failedOnBase.get();
            return new CheckFailure(check, FailureClass.UNRELATED_FAILS_ON_BASE,
                    "failed on base " + c.oid().substring(0, Math.min(7, c.oid().length())) + " ("
                            + DAY.format(c.committedDate()) + ")");
        }
        String onPeers = failsOnPeers(check.name(), peers);
        if (onPeers != null) {
            return new CheckFailure(check, FailureClass.UNRELATED_FAILS_ON_OTHER_PRS, onPeers);
        }
        List<Outcome> tries = attempts.attemptsByName().getOrDefault(check.name(), List.of());
        if (tries.contains(Outcome.SUCCESS)) {
            return new CheckFailure(check, FailureClass.LIKELY_FLAKY, "passed in an earlier attempt on this commit");
        }
        return new CheckFailure(check, FailureClass.NOT_ANALYSED, null);
    }

    /** @return the evidence when the rule applies, else null */
    private static String failsOnPeers(String name, Peers peers) {
        List<PeerPr> failed = new ArrayList<>();
        int passed = 0;
        for (PeerPr p : peers.prs()) {
            Outcome o = outcomeOf(name, p.checks());
            if (o == Outcome.FAILURE) {
                failed.add(p);
            } else if (o == Outcome.SUCCESS) {
                passed++;
            }
        }
        if (failed.size() < peers.minFailures() || failed.size() < passed) {
            return null;
        }
        String numbers = failed.stream().limit(LISTED).map(p -> "#" + p.number()).collect(Collectors.joining(", "))
                + (failed.size() > LISTED ? " +" + (failed.size() - LISTED) + " more" : "");
        return peers.mine()
                ? "also fails on my " + numbers
                : "also fails on " + failed.size() + " of " + (failed.size() + passed) + " other open PRs that ran it ("
                        + numbers + ")";
    }

    private static Optional<BaseCommit> mostRecentFinishedOnBase(String name, BaseHistory base) {
        return base.commits().stream().filter(c -> {
            Outcome o = outcomeOf(name, c.checks());
            return o == Outcome.SUCCESS || o == Outcome.FAILURE;
        }).findFirst();
    }

    /** FAILURE if any same-named check failed, else SUCCESS if one passed, else null. */
    private static Outcome outcomeOf(String name, List<CheckContext> checks) {
        Outcome result = null;
        for (CheckContext c : checks) {
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
