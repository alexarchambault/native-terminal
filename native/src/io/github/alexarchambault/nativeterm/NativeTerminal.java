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
     * <p>On Linux / Mac, this uses an {@code ioctl(TIOCGWINSZ)} call on stdout. If stdout
     * isn't a terminal (when it's piped or redirected, for example), this falls back to
     * stderr. The {@code ioctl} call is done via the Foreign Function and Memory API on
     * Java &gt;= 22, and via JNI, using libraries that ship with jline-native, on older
     * Java versions.
     *
     * <p>On Windows, this uses a {@code kernel32.dll} method.
     *
     * @throws IOException if anything goes wrong, or if neither stdout nor stderr
     *                     are terminals on Linux / Mac
     * @return the terminal size
     */
    public static TerminalSize getSize() throws IOException {
        if (isWindows)
            return WindowsTerm.getSize();
        try {
            return UnixTerm.getSize(true);
        } catch (IOException stdoutEx) {
            try {
                return UnixTerm.getSize(false);
            } catch (IOException stderrEx) {
                stdoutEx.addSuppressed(stderrEx);
                throw stdoutEx;
            }
        }
    }

    /**
     * Enables ANSI terminal output (only needed on Windows)
     *
     * <p>It is safe to call this method on non-Windows systems. It simply
     * returns true in that case.
     *
     * <p>Under-the-hood, this calls a {@code kernel32.dll} method, via JNI,
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
