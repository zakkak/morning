package net.zakkak.morning.prs;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.enterprise.context.ApplicationScoped;
import net.zakkak.morning.config.MyPrsSection;
import net.zakkak.morning.github.GraphQLQueries;
import net.zakkak.morning.github.Query;

/**
 * Fetches and analyses one {@code my-prs} section with at most 2 requests plus 1 per extra page of
 * checks (SC-001):
 * <ol>
 * <li>{@code MyPrs}: search with rollups;</li>
 * <li>{@code PrChecksPage}: only for PRs with more than 100 checks;</li>
 * <li>{@code BaseAndAttempts}: only when some check fails.</li>
 * </ol>
 */
@ApplicationScoped
public class MyPrsFetcher {

    static final int PAGE_SIZE = 50;

    Clock clock = Clock.systemUTC();

    public MyPrsReport fetch(MyPrsSection section, GraphQLQueries github) {
        int before = github.requests();
        List<String> warnings = new ArrayList<>();

        JsonNode search = github.execute(Query.MY_PRS, Map.of("q", searchQuery(section))).path("search");
        List<PullRequestNode> prs = new ArrayList<>();
        for (JsonNode node : search.path("nodes")) {
            if (node.hasNonNull("id")) {
                prs.add(PrJson.pullRequest(node));
            }
        }
        int total = search.path("issueCount").asInt(prs.size());
        if (total > prs.size()) {
            warnings.add("showing " + prs.size() + " of " + total + " open PRs");
        }

        for (int i = 0; i < prs.size(); i++) {
            prs.set(i, allChecks(prs.get(i), github));
        }

        Map<String, BaseHistory> bases = new HashMap<>();
        Map<String, CheckAttempts> attempts = new HashMap<>();
        List<PullRequestNode> failing = prs.stream().filter(PullRequestNode::hasFailingCheck).toList();
        if (!failing.isEmpty()) {
            List<String> refIds = new ArrayList<>(new LinkedHashSet<>(
                    failing.stream().map(PullRequestNode::baseRefId).filter(id -> id != null).toList()));
            List<String> prIds = failing.stream().map(PullRequestNode::id).toList();
            JsonNode data = github.execute(Query.BASE_AND_ATTEMPTS, Map.of("refIds", refIds, "prIds", prIds));
            for (JsonNode ref : data.path("bases")) {
                if (ref.hasNonNull("id")) {
                    BaseHistory h = PrJson.baseHistory(ref);
                    bases.put(h.refId(), h);
                }
            }
            for (JsonNode pr : data.path("attempts")) {
                if (pr.hasNonNull("id")) {
                    CheckAttempts a = PrJson.attempts(pr);
                    attempts.put(a.prId(), a);
                }
            }
        }

        List<PullRequestStatus> statuses = new ArrayList<>();
        for (PullRequestNode pr : prs) {
            List<CheckFailure> failures = FailureClassifier.classify(pr,
                    bases.getOrDefault(pr.baseRefId(), BaseHistory.EMPTY),
                    attempts.getOrDefault(pr.id(), CheckAttempts.EMPTY));
            statuses.add(PrSorter.analyse(pr, failures));
        }
        return new MyPrsReport(section.name(), clock.instant(), PrSorter.sort(statuses),
                github.requests() - before, warnings);
    }

    private static PullRequestNode allChecks(PullRequestNode pr, GraphQLQueries github) {
        PullRequestNode current = pr;
        while (current.checksHasNextPage() && current.checksEndCursor() != null) {
            JsonNode contexts = github.execute(Query.PR_CHECKS_PAGE,
                    Map.of("id", current.id(), "after", current.checksEndCursor()))
                    .path("node").path("commits").path("nodes").path(0).path("commit")
                    .path("statusCheckRollup").path("contexts");
            current = current.withMoreChecks(PrJson.checks(contexts),
                    contexts.path("pageInfo").path("hasNextPage").asBoolean(false),
                    PrJson.text(contexts.path("pageInfo"), "endCursor"));
        }
        return current;
    }

    /** GitHub search qualifiers only; org names are validated by the config loader. */
    static String searchQuery(MyPrsSection section) {
        StringBuilder q = new StringBuilder("is:pr is:open author:@me archived:false");
        section.includeOrgs().forEach(o -> q.append(" org:").append(o));
        section.excludeOrgs().forEach(o -> q.append(" -org:").append(o));
        q.append(" sort:updated-asc");
        return q.toString();
    }
}
