package net.zakkak.morning.config;

/** Shared validation patterns. */
final class Patterns {
    /** A GitHub user or organisation login. */
    static final String OWNER = "^[A-Za-z0-9-]{1,39}$";
    /** {@code owner/name}. */
    static final String REPO = "^[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}$";

    private Patterns() {
    }
}
