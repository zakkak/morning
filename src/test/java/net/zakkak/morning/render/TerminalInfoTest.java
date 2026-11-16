package net.zakkak.morning.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TerminalInfoTest {
    @Test
    void widthPrecedence() {
        assertEquals(70, TerminalInfo.width(70, Map.of("COLUMNS", "90")::get, () -> 100));
        assertEquals(90, TerminalInfo.width(null, Map.of("COLUMNS", "90")::get, () -> 100));
        assertEquals(100, TerminalInfo.width(null, Map.of("COLUMNS", "x")::get, () -> 100));
        assertEquals(120, TerminalInfo.width(null, k -> null, () -> null));
    }
}
