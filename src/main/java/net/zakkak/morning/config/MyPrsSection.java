package net.zakkak.morning.config;

import java.util.List;

import jakarta.validation.constraints.Pattern;

/**
 * Open PRs authored by the user, optionally limited to (or excluding) some owners.
 * Overlap between the two lists is checked by {@link ConfigLoader}, so the error can name the
 * field.
 */
public record MyPrsSection(
        String name,
        List<@Pattern(regexp = Patterns.OWNER, message = "must be a GitHub login") String> includeOrgs,
        List<@Pattern(regexp = Patterns.OWNER, message = "must be a GitHub login") String> excludeOrgs)
        implements Section {

    public static final String TYPE = "my-prs";

    public MyPrsSection {
        name = name == null ? TYPE : name;
        includeOrgs = includeOrgs == null ? List.of() : List.copyOf(includeOrgs);
        excludeOrgs = excludeOrgs == null ? List.of() : List.copyOf(excludeOrgs);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
