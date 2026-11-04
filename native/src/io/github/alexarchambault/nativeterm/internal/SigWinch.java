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
     * <p>This is a no-op on Windows, or if the WINCH signal isn't supported by the current JVM.
     *
     * @param runnable A Runnable, run every time a WINCH signal is received
     */
    public static void addHandler(Runnable runnable) {

        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("windows");
        if (isWindows)
            return;

        Signal signal;
        try {
            signal = new Signal("WINCH");
        } catch (IllegalArgumentException e) {
            // WINCH not supported here
            return;
        }

        SignalHandler handler = new SignalHandler() {
            @Override
            public void handle(Signal arg0) {
                runnable.run();
            }
        };

        try {
            Signal.handle(signal, handler);
        } catch (IllegalArgumentException e) {
            // signal already used by the JVM, or not supported
            return;
        }
    }

}
