package net.zakkak.morning.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** One repository's Actions CI (ADR-0005). Parsed now; implemented by feature 002. */
public record CiStatusSection(
        String name,
        @NotNull @Pattern(regexp = Patterns.REPO, message = "must be owner/name") String repo,
        List<String> branches,
        List<String> workflows,
        List<String> events,
        Boolean includeForkPrs,
        @Valid Tracking tracking,
        List<@Pattern(regexp = Patterns.REPO, message = "must be owner/name") String> relatedRepos)
        implements Section {

    public static final String TYPE = "ci-status";

    public CiStatusSection {
        name = name == null ? TYPE + " " + repo : name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /** How failing jobs are mapped to long-lived tracking issues. */
    public record Tracking(@NotNull Strategy strategy, List<String> issueKeys) {
    }

    public enum Strategy {
        @com.fasterxml.jackson.annotation.JsonProperty("none")
        NONE,
        @com.fasterxml.jackson.annotation.JsonProperty("workflow-inputs")
        WORKFLOW_INPUTS
    }
}
