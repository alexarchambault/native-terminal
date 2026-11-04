package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.nativeterm.internal.SigWinch;

import java.io.IOException;

/**
 * Helper caching the terminal size, and re-querying it only if a SIGWINCH has been received
 *
 * <p>On Windows, this doesn't cache anything and just re-queries the terminal size upon
 * every {@link #getSize()} call.
 */
public final class TerminalSizeCache {
    /**
     * Create a new TerminalSizeCache
     *
     * <p>This sets up a signal handler for the WINCH signal.
     *
     * <p>As much as possible, only create a single instance of this class,
     * and re-use it through your application.
     */
    public TerminalSizeCache() {
        SigWinch.addHandler(
                new Runnable() {
                    @Override
                    public void run() {
                        terminalSize = null;
                    }
                }
        );
    }

    private TerminalSize terminalSize = null;

    /**
     * Get the terminal size
     *
     * <p>If the terminal size is in cache, it's returned straightaway.
     *
     * <p>If the terminal size isn't in cache (not queried yet or recently invalidated by a SIGWINCH),
     * it's queried, put in cache, and returned.
     *
     * @throws IOException if anything goes wrong
     * @return the terminal size
     */
    public TerminalSize getSize() throws IOException {
        if (NativeTerminal.isWindows)
            return NativeTerminal.getSize();

        TerminalSize terminalSize0 = terminalSize;
        if (terminalSize0 == null)
            terminalSize0 = update();
        return terminalSize0;
    }

    private TerminalSize update() throws IOException {
        TerminalSize terminalSize0 = NativeTerminal.getSize();
        terminalSize = terminalSize0;
        return terminalSize0;
    }
}
