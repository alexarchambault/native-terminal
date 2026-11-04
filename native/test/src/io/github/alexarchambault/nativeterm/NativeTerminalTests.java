package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.isterminal.IsTerminal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeTerminalTests {

    @Test
    void setupAnsi() throws Exception {
        // only does something on Windows, if attached to a terminal
        NativeTerminal.setupAnsi();
    }

    /** Only asserts things when attached to a terminal (which is usually not the case here). */
    @Test
    void getSize() throws Exception {
        assumeTrue(IsTerminal.isTerminal(), "not attached to a terminal");
        TerminalSize size = NativeTerminal.getSize();
        assertNotNull(size);
        assertTrue(size.getWidth() > 0, "width: " + size);
        assertTrue(size.getHeight() > 0, "height: " + size);
    }
}
