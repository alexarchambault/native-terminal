package io.github.alexarchambault.nativeterm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalSizeTests {

    @Test
    void accessors() {
        TerminalSize size = TerminalSize.of(120, 40);
        assertEquals(120, size.getWidth());
        assertEquals(40, size.getHeight());
    }

    @Test
    void equalsAndHashCode() {
        TerminalSize size = TerminalSize.of(120, 40);
        TerminalSize same = TerminalSize.of(120, 40);
        assertEquals(size, size);
        assertEquals(size, same);
        assertEquals(same, size);
        assertEquals(size.hashCode(), same.hashCode());

        assertNotEquals(size, TerminalSize.of(40, 120));
        assertNotEquals(size, TerminalSize.of(120, 41));
        assertNotEquals(size, TerminalSize.of(121, 40));
        assertNotEquals(size, null);
        assertNotEquals(size, "TerminalSize{width=120, height=40}");
    }

    @Test
    void toStringFormat() {
        assertEquals("TerminalSize{width=120, height=40}", TerminalSize.of(120, 40).toString());
        assertEquals("TerminalSize{width=0, height=0}", TerminalSize.of(0, 0).toString());
    }
}
