package net.zakkak.morning.render;

/**
 * A table cell. Text may contain {@code \n} for separate lines. The URL is applied at render time
 * (OSC-8 on a TTY, a markdown link otherwise), so it never affects the width calculation.
 */
public record Cell(String text, String url) {
    public Cell {
        text = text == null ? "" : text;
    }

    public static Cell of(String text) {
        return new Cell(text, null);
    }
}
