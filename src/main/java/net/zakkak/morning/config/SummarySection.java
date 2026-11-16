package net.zakkak.morning.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A prioritised summary over the other sections' findings. Parsed now; feature 004. */
public record SummarySection(String name, List<Category> order) implements Section {

    public static final String TYPE = "summary";

    public SummarySection {
        name = name == null ? TYPE : name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    public enum Category {
        @JsonProperty("changes-requested")
        CHANGES_REQUESTED,
        @JsonProperty("my-ci-failures")
        MY_CI_FAILURES,
        @JsonProperty("new-ci-failures")
        NEW_CI_FAILURES,
        @JsonProperty("needs-owner")
        NEEDS_OWNER,
        @JsonProperty("reviews")
        REVIEWS
    }
}
