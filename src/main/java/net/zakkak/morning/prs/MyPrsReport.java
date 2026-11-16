package net.zakkak.morning.prs;

import java.time.Instant;
import java.util.List;

/**
 * The result of one {@code my-prs} section.
 *
 * @param prs sorted by group, then oldest update first
 * @param apiRequests GitHub requests made (SC-001)
 * @param warnings non-fatal notes, e.g. results truncated
 */
public record MyPrsReport(String section, Instant generatedAt, List<PullRequestStatus> prs, int apiRequests,
        List<String> warnings) {
    public MyPrsReport {
        prs = List.copyOf(prs);
        warnings = List.copyOf(warnings);
    }
}
