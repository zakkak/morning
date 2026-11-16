package net.zakkak.morning.prs;

/**
 * One check on a commit: a GitHub Actions/App check run or a commit status.
 *
 * @param url details URL, if any
 * @param app the GitHub App slug for check runs (e.g. {@code github-actions}), else null
 */
public record CheckContext(String name, Kind kind, Outcome outcome, String url, String app) {

    public enum Kind {
        CHECK_RUN, STATUS_CONTEXT
    }
}
