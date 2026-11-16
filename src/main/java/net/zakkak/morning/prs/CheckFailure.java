package net.zakkak.morning.prs;

/**
 * @param evidence why the class was chosen, e.g. "failed on base 1a2b3c4 (2026-11-12)", or null
 */
public record CheckFailure(CheckContext check, FailureClass failureClass, String evidence) {
}
