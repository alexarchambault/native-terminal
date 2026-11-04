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
     * <p>Handlers registered earlier (by this class or by other libraries) are kept, and
     * run after the new one.
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

        // The previous handler is only known once Signal.handle returns, and our handler
        // may be called before that, hence this holder
        final SignalHandler[] previous = new SignalHandler[1];

        SignalHandler handler = new SignalHandler() {
            @Override
            public void handle(Signal sig) {
                try {
                    runnable.run();
                } finally {
                    SignalHandler previous0;
                    synchronized (previous) {
                        previous0 = previous[0];
                    }
                    if (previous0 != null &&
                            previous0 != SignalHandler.SIG_DFL &&
                            previous0 != SignalHandler.SIG_IGN)
                        previous0.handle(sig);
                }
            }
        };

        SignalHandler previous0;
        try {
            previous0 = Signal.handle(signal, handler);
        } catch (IllegalArgumentException e) {
            // signal already used by the JVM, or not supported
            return;
        }
        synchronized (previous) {
            previous[0] = previous0;
        }
    }

}
