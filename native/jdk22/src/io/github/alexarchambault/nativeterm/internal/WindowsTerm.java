package io.github.alexarchambault.nativeterm.internal;

import io.github.alexarchambault.nativeterm.TerminalSize;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

/**
 * Utilities to interact with the terminal on Windows in a native fashion
 */
public final class WindowsTerm {

    private WindowsTerm() {}

    static {
        // Make https://github.com/fusesource/hawtjni/blob/c14fec00b9976ff6b84e62e483d678594a7d3832/hawtjni-runtime/src/main/java/org/fusesource/hawtjni/runtime/Library.java#L167 happy
        // Don't know if that's needed anymore with jline-native
        if (System.getProperty("com.ibm.vm.bitmode") == null)
            System.setProperty("com.ibm.vm.bitmode", "64");
    }

    // adapted from https://github.com/jline/jline3/blob/8bb13a89fad80e51726a29e4b1f8a0724fed78b2/terminal-jna/src/main/java/org/jline/terminal/impl/jna/win/JnaWinSysTerminal.java#L92-L96
    /**
     * Gets the terminal size
     * @return the terminal size
     * @throws IOException if anything goes wrong
     */
    public static TerminalSize getSize() throws IOException {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment console = Kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
            Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO();
            Kernel32.GetConsoleScreenBufferInfo(console, info);
            return TerminalSize.of(info.windowWidth(), info.windowHeight());
        }
    }

    /**
     * Enables ANSI output
     * @return whether ANSI output is enabled
     * @throws IOException if anything goes wrong
     */
    public static boolean setupAnsi() throws IOException {

        // from https://github.com/jline/jline3/blob/0660ae29f3af2ca3b56cdeca1530072306988e4d/terminal/src/main/java/org/jline/terminal/impl/AbstractWindowsTerminal.java#L53
        final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment console = Kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
            java.lang.foreign.MemorySegment mode = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
            if (Kernel32.GetConsoleMode(console, mode) == 0)
                throw new IOException("Failed to get console mode: " + Kernel32.getLastErrorMessage());
            // FIXME Check if ENABLE_VIRTUAL_TERMINAL_PROCESSING is already set in mode[0] ??
            return Kernel32.SetConsoleMode(console, mode.get(java.lang.foreign.ValueLayout.JAVA_INT, 0) | ENABLE_VIRTUAL_TERMINAL_PROCESSING) != 0;
        }
    }

}
