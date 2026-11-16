package net.zakkak.morning.config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Patterns of credentials that must never appear in configuration (and, from feature 002,
 * never reach the LLM). Shared so both checks stay in step.
 */
public final class SecretPatterns {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{36,}"),
            Pattern.compile("github_pat_[A-Za-z0-9_]{20,}"),
            Pattern.compile("sk-ant-[A-Za-z0-9_-]{20,}"));

    private SecretPatterns() {
    }

    public static boolean containsSecret(String value) {
        return value != null && PATTERNS.stream().anyMatch(p -> p.matcher(value).find());
    }
}
