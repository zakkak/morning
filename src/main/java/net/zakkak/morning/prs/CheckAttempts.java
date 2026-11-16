package net.zakkak.morning.prs;

import java.util.List;
import java.util.Map;

/** Outcomes of every attempt of every check run on a PR's head commit, by check name. */
public record CheckAttempts(String prId, Map<String, List<Outcome>> attemptsByName) {
    public static final CheckAttempts EMPTY = new CheckAttempts(null, Map.of());

    public CheckAttempts {
        attemptsByName = Map.copyOf(attemptsByName);
    }
}
