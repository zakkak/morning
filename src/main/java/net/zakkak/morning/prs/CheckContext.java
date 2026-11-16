package net.zakkak.morning.prs;

import java.time.Instant;

/**
 * One check on a commit: a GitHub Actions/App check run or a commit status.
 *
 * @param url details URL, if any
 * @param app the GitHub App slug for check runs (e.g. {@code github-actions}), else null
 * @param at when the result was produced (check run completion, or status creation); null
 *        while pending
 */
public record CheckContext(String name, Kind kind, Outcome outcome, String url, String app, Instant at) {

    public enum Kind {
        CHECK_RUN, STATUS_CONTEXT
    }
}
