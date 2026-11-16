package net.zakkak.morning.prs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/** Maps responses of the my-prs GraphQL documents to records. Field names follow GitHub's schema. */
final class PrJson {

    private PrJson() {
    }

    static PullRequestNode pullRequest(JsonNode n) {
        JsonNode rollup = n.path("commits").path("nodes").path(0).path("commit").path("statusCheckRollup");
        JsonNode contexts = rollup.path("contexts");
        return new PullRequestNode(
                text(n, "id"),
                n.path("number").asInt(),
                text(n, "title"),
                text(n, "url"),
                n.path("isDraft").asBoolean(),
                enumOr(Mergeable.class, text(n, "mergeable"), Mergeable.UNKNOWN),
                enumOr(ReviewDecision.class, text(n, "reviewDecision"), null),
                Instant.parse(text(n, "updatedAt")),
                text(n.path("repository"), "nameWithOwner"),
                text(n.path("repository"), "id"),
                text(n, "baseRefName"),
                text(n.path("baseRef"), "id"),
                text(n, "headRefOid"),
                enumOr(RollupState.class, text(rollup, "state"), null),
                checks(contexts),
                contexts.path("pageInfo").path("hasNextPage").asBoolean(false),
                text(contexts.path("pageInfo"), "endCursor"));
    }

    static List<CheckContext> checks(JsonNode contexts) {
        List<CheckContext> checks = new ArrayList<>();
        for (JsonNode c : contexts.path("nodes")) {
            CheckContext check = check(c);
            if (check != null) {
                checks.add(check);
            }
        }
        return checks;
    }

    static CheckContext check(JsonNode c) {
        return switch (text(c, "__typename") == null ? "" : text(c, "__typename")) {
            case "CheckRun" -> new CheckContext(text(c, "name"), CheckContext.Kind.CHECK_RUN,
                    Outcome.ofCheckRun(text(c, "status"), text(c, "conclusion")), text(c, "detailsUrl"),
                    text(c.path("checkSuite").path("app"), "slug"), instant(c, "completedAt"));
            case "StatusContext" -> new CheckContext(text(c, "context"), CheckContext.Kind.STATUS_CONTEXT,
                    Outcome.ofStatus(text(c, "state")), text(c, "targetUrl"), null, instant(c, "createdAt"));
            default -> null;
        };
    }

    static BaseHistory baseHistory(JsonNode ref) {
        List<BaseCommit> commits = new ArrayList<>();
        for (JsonNode c : ref.path("target").path("history").path("nodes")) {
            commits.add(new BaseCommit(text(c, "oid"), Instant.parse(text(c, "committedDate")),
                    checks(c.path("statusCheckRollup").path("contexts"))));
        }
        return new BaseHistory(text(ref, "id"), commits);
    }

    static CheckAttempts attempts(JsonNode pr) {
        Map<String, List<Outcome>> byName = new HashMap<>();
        JsonNode suites = pr.path("commits").path("nodes").path(0).path("commit").path("checkSuites").path("nodes");
        for (JsonNode suite : suites) {
            for (JsonNode run : suite.path("checkRuns").path("nodes")) {
                byName.computeIfAbsent(text(run, "name"), k -> new ArrayList<>())
                        .add(Outcome.ofCheckRun(text(run, "status"), text(run, "conclusion")));
            }
        }
        byName.remove(null);
        return new CheckAttempts(text(pr, "id"), byName);
    }

    /** Open PRs of one repository, from the {@code others} block of BaseAndAttempts. */
    static List<PeerPr> peers(JsonNode repo) {
        List<PeerPr> peers = new ArrayList<>();
        for (JsonNode pr : repo.path("pullRequests").path("nodes")) {
            if (pr.hasNonNull("id")) {
                peers.add(new PeerPr(text(pr, "id"), pr.path("number").asInt(), text(pr, "baseRefName"),
                        text(pr, "headRefOid"), checks(pr.path("commits").path("nodes").path(0).path("commit")
                                .path("statusCheckRollup").path("contexts"))));
            }
        }
        return peers;
    }

    private static Instant instant(JsonNode node, String field) {
        String v = text(node, field);
        return v == null ? null : Instant.parse(v);
    }

    static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
