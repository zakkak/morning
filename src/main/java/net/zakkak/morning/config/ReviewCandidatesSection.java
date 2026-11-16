package net.zakkak.morning.config;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** PRs in one repository that match the user's review interests. Parsed now; feature 003. */
public record ReviewCandidatesSection(
        String name,
        @NotNull @Pattern(regexp = Patterns.REPO, message = "must be owner/name") String repo,
        List<String> labels,
        List<String> paths,
        List<String> titleKeywords,
        Map<String, Double> weights,
        List<String> onHoldLabels,
        List<@Pattern(regexp = Patterns.REPO, message = "must be owner/name") String> relatedRepos)
        implements Section {

    public static final String TYPE = "review-candidates";

    public ReviewCandidatesSection {
        name = name == null ? TYPE + " " + repo : name;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
