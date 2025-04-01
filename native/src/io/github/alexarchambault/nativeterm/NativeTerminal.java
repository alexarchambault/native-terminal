package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.isterminal.IsTerminal;
import io.github.alexarchambault.nativeterm.internal.UnixTerm;
import io.github.alexarchambault.nativeterm.internal.WindowsTerm;

import java.io.IOException;

/**
 * Use these methods to query the terminal size and enable ANSI output in it
 */
public final class NativeTerminal {
    private NativeTerminal() {}

    final static boolean isWindows;

    static {
        isWindows = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("windows");
    }

    /**
     * Gets the terminal size
     *
     * This uses an {@code ioctl} call on Linux / Mac, and a {@code kernel32.dll} method
     * on Windows. Both are done via JNI, using libraries that ship with jline-native.
     *
     * @return the terminal size
     */
    public static TerminalSize getSize() {
        if (isWindows)
            return WindowsTerm.getSize();
        return UnixTerm.getSize(true);
    }

    /**
     * Enables ANSI terminal output (only needed on Windows)
     *
     * It is safe to call this method on non-Windows systems. It simply
     * returns true in that case.
     *
     * Under-the-hood, this calls a {@code kernel32.dll} method, via JNI,
     * using libraries that ship with jline-native.
     *
     * @throws IOException if anything goes wrong
     * @return Whether ANSI output is enabled
     */
    public static boolean setupAnsi() throws IOException {
        if (isWindows && IsTerminal.isTerminal())
            return WindowsTerm.setupAnsi();
        return true;
    }
}
