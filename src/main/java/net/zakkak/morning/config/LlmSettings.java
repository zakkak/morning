package net.zakkak.morning.config;

import jakarta.validation.constraints.Min;

/** Top-level {@code llm} settings. Parsed now; used from feature 002. */
public record LlmSettings(Boolean enabled, @Min(0) Integer maxCalls) {

    public static final LlmSettings DEFAULT = new LlmSettings(true, 15);

    public LlmSettings {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        maxCalls = maxCalls == null ? 15 : maxCalls;
    }
}
