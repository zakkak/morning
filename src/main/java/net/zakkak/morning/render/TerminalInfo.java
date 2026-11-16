package net.zakkak.morning.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/** Terminal width and TTY detection (research R9). No JLine. */
public final class TerminalInfo {

    public static final int DEFAULT_WIDTH = 120;

    private TerminalInfo() {
    }

    /** {@code --width}, then {@code $COLUMNS}, then {@code stty size}, then 120. */
    public static int width(Integer option) {
        return width(option, System::getenv, TerminalInfo::sttyColumns);
    }

    static int width(Integer option, Function<String, String> env, Supplier<Integer> stty) {
        if (option != null && option > 0) {
            return option;
        }
        Integer columns = positive(env.apply("COLUMNS"));
        if (columns != null) {
            return columns;
        }
        Integer fromStty = stty.get();
        return fromStty != null && fromStty > 0 ? fromStty : DEFAULT_WIDTH;
    }

    public static boolean stdoutIsTerminal() {
        return System.console() != null && System.console().isTerminal();
    }

    private static Integer positive(String value) {
        try {
            int v = Integer.parseInt(value == null ? "" : value.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer sttyColumns() {
        try {
            Process p = new ProcessBuilder(List.of("stty", "size"))
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/tty")))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String[] parts = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().split("\\s+");
            return p.exitValue() == 0 && parts.length == 2 ? positive(parts[1]) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
