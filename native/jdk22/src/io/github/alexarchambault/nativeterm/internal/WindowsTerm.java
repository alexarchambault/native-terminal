package io.github.alexarchambault.nativeterm.internal;

import io.github.alexarchambault.nativeterm.TerminalSize;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

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
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment callState = Kernel32.allocateCallState(arena);
            MemorySegment console = Kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
            Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO(arena);
            Kernel32.GetConsoleScreenBufferInfo(callState, console, info);
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

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment callState = Kernel32.allocateCallState(arena);
            MemorySegment console = Kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
            MemorySegment mode = arena.allocate(ValueLayout.JAVA_INT);
            if (Kernel32.GetConsoleMode(callState, console, mode) == 0)
                throw new IOException("Failed to get console mode: " + Kernel32.lastErrorMessage(callState));
            int currentMode = mode.get(ValueLayout.JAVA_INT, 0);
            if ((currentMode & ENABLE_VIRTUAL_TERMINAL_PROCESSING) != 0)
                return true;
            return Kernel32.SetConsoleMode(callState, console, currentMode | ENABLE_VIRTUAL_TERMINAL_PROCESSING) != 0;
        }
    }

}
