package io.github.alexarchambault.nativeterm.internal;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.Locale;

/**
 * Helper to register handlers of the WINCH signal (terminal size change)
 */
public final class SigWinch {
    private SigWinch() {}

    /**
     * Register a new WINCH handler
     *
     * <p>This is a no-op on Windows.
     *
     * @param runnable A Runnable, run every time a WINCH signal is received
     */
    public static void addHandler(Runnable runnable) {

        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("windows");
        if (isWindows)
            return;

        SignalHandler handler = new SignalHandler() {
            @Override
            public void handle(Signal arg0) {
                runnable.run();
            }
        };

        Signal.handle(new Signal("WINCH"), handler);
    }

}
