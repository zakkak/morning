package net.zakkak.morning.github;

/** A GitHub request failed. Messages come from GitHub's response and never contain the token. */
public class GitHubException extends RuntimeException {
    public GitHubException(String message) {
        super(message);
    }

    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
