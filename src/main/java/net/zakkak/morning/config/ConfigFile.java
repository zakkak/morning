package net.zakkak.morning.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** The YAML document root. Exists so that error paths start with {@code morning.}. */
record ConfigFile(@NotNull @Valid MorningConfig morning) {
}
