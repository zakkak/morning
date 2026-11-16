package net.zakkak.morning.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One entry of the routine (ADR-0008). The YAML {@code type} key selects the record.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MyPrsSection.class, name = MyPrsSection.TYPE),
        @JsonSubTypes.Type(value = CiStatusSection.class, name = CiStatusSection.TYPE),
        @JsonSubTypes.Type(value = ReviewCandidatesSection.class, name = ReviewCandidatesSection.TYPE),
        @JsonSubTypes.Type(value = SummarySection.class, name = SummarySection.TYPE) })
public sealed interface Section permits MyPrsSection, CiStatusSection, ReviewCandidatesSection, SummarySection {

    /** The YAML type name, e.g. {@code my-prs}. */
    String type();

    /** The section name, unique within the config. Defaults are derived per type. */
    String name();
}
