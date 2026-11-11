package io.github.alexarchambault.nativeterm.internal;

import io.github.alexarchambault.nativeterm.NativeTerminal;
import io.github.alexarchambault.nativeterm.TerminalSize;

import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Emulates the WINCH signal on Windows, by polling the terminal size from a daemon thread
 *
 * <p>Windows has no WINCH signal. The console can report size changes as
 * {@code WINDOW_BUFFER_SIZE_EVENT} input records (see
 * <a href="https://stackoverflow.com/questions/10856926/sigwinch-equivalent-on-windows">this StackOverflow question</a>),
 * but these can only be received by reading the console input buffer with {@code ReadConsoleInput}, which
 * would steal the key events of applications reading their standard input. Peeking at the input buffer
 * instead isn't an option either: events not consumed by anyone stay there, so we'd never block waiting
 * for new ones. So we poll {@code GetConsoleScreenBufferInfo} instead, which is cheap and has no side effects.
 *
 * <p>The polling interval, in milliseconds, can be adjusted via the
 * {@code native-terminal.windows.size-poll-interval} Java property (250 by default).
 */
public final class WindowsSizeWatcher {

    private static final long defaultPollInterval = 250L;

    private static long pollInterval() {
        String value = System.getProperty("native-terminal.windows.size-poll-interval");
        if (value != null) {
            try {
                long interval = Long.parseLong(value.trim());
                if (interval > 0)
                    return interval;
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultPollInterval;
    }

    private static WindowsSizeWatcher instance = null;

    private static synchronized WindowsSizeWatcher instance() {
        if (instance == null)
            instance = new WindowsSizeWatcher(
                    new Callable<TerminalSize>() {
                        @Override
                        public TerminalSize call() throws Exception {
                            return NativeTerminal.getSize();
                        }
                    },
                    pollInterval()
            );
        return instance;
    }

    /**
     * Register a handler, run every time a terminal size change is detected
     *
     * <p>This starts the polling thread, if it wasn't started already.
     *
     * @param runnable A Runnable, run from the polling thread every time a terminal size change is detected
     */
    public static void addHandler(Runnable runnable) {
        instance().add(runnable);
    }

    private final Callable<TerminalSize> getSize;
    private final long pollInterval;
    private final CopyOnWriteArrayList<Runnable> handlers = new CopyOnWriteArrayList<>();
    private Thread thread = null;

    /**
     * Create a watcher
     *
     * <p>Only meant to be used from tests. Use {@link SigWinch} instead.
     *
     * @param getSize Queries the terminal size, may throw or return null if it can't
     * @param pollInterval Polling interval, in milliseconds
     */
    public WindowsSizeWatcher(Callable<TerminalSize> getSize, long pollInterval) {
        this.getSize = getSize;
        this.pollInterval = pollInterval;
    }

    /**
     * Register a handler, and start the polling thread if it wasn't started already
     *
     * @param runnable A Runnable, run from the polling thread every time a terminal size change is detected
     */
    public synchronized void add(Runnable runnable) {
        handlers.add(runnable);
        if (thread == null) {
            thread = new Thread("native-terminal-windows-size-watcher") {
                @Override
                public void run() {
                    watch();
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Stop the polling thread
     *
     * <p>Only meant to be used from tests.
     */
    public synchronized void stop() {
        if (thread != null)
            thread.interrupt();
    }

    private TerminalSize currentSize() {
        try {
            return getSize.call();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void watch() {
        TerminalSize previous = currentSize();
        while (true) {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                return;
            }
            TerminalSize size = currentSize();
            if (size != null && !size.equals(previous)) {
                previous = size;
                for (Runnable handler : handlers) {
                    try {
                        handler.run();
                    } catch (RuntimeException ignored) {
                        // don't let a faulty handler prevent the others from being run
                    }
                }
            }
        }
    }
}
