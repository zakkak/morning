package net.zakkak.morning.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** The user's routine: the {@code morning} key of config.yaml. */
public record MorningConfig(@NotEmpty List<@Valid Section> sections, @Valid LlmSettings llm) {

    public MorningConfig {
        sections = sections == null ? null : List.copyOf(sections);
        llm = llm == null ? LlmSettings.DEFAULT : llm;
    }
}
