package io.github.alexarchambault.nativeterm.internal;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Helper to register handlers of the WINCH signal (terminal size change)
 *
 * <p>Windows has no WINCH signal. There, the terminal size is polled from a daemon thread
 * instead, and handlers are run when it changes (see {@link WindowsSizeWatcher}).
 */
public final class SigWinch {
    private SigWinch() {}

    private static final Consumer<Throwable> ignoreErrors = new Consumer<Throwable>() {
        @Override
        public void accept(Throwable t) {}
    };

    /**
     * Register a new WINCH handler
     *
     * <p>Same as {@link #addHandler(Runnable, Consumer)}, ignoring errors.
     *
     * @param runnable A Runnable, run every time a WINCH signal is received
     */
    public static void addHandler(Runnable runnable) {
        addHandler(runnable, ignoreErrors);
    }

    /**
     * Register a new WINCH handler
     *
     * <p>Handlers registered earlier (by this class or by other libraries) are kept, and
     * run after the new one.
     *
     * <p>On Windows, {@code runnable} is run every time a terminal size change is detected,
     * and {@code onError} is never called. If the WINCH signal can't be handled on other platforms
     * (it's not supported by the current JVM, or it's already used by the JVM), no handler is
     * registered, and the exception thrown when trying to register one is passed to
     * {@code onError}.
     *
     * @param runnable A Runnable, run every time a WINCH signal is received (or, on Windows,
     *                 every time a terminal size change is detected)
     * @param onError Called with the exception thrown if the handler can't be registered
     */
    public static void addHandler(Runnable runnable, Consumer<Throwable> onError) {

        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("windows");
        if (isWindows)
            WindowsSizeWatcher.addHandler(runnable);
        else
            addSignalHandler(runnable, onError);
    }

    private static void addSignalHandler(Runnable runnable, Consumer<Throwable> onError) {

        Signal signal;
        try {
            signal = new Signal("WINCH");
        } catch (IllegalArgumentException e) {
            // WINCH not supported here
            onError.accept(e);
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
            onError.accept(e);
            return;
        }
        synchronized (previous) {
            previous[0] = previous0;
        }
    }

}
