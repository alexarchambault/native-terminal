package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.nativeterm.internal.SigWinch;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SigWinchTests {

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    /** Current process id (ProcessHandle isn't available on Java 8) */
    private static String pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.substring(0, name.indexOf('@'));
    }

    /**
     * Registers a handler, and sends WINCH to the current process, which exercises the
     * sun.misc.Signal calls of SigWinch.
     */
    @Test
    void handlerCalled() throws Exception {
        assumeFalse(isWindows(), "no WINCH signal on Windows");

        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        SigWinch.addHandler(called::countDown, error::set);
        assertNull(error.get(), "error registering handler");

        Process kill = new ProcessBuilder("kill", "-WINCH", pid()).inheritIO().start();
        assertEquals(0, kill.waitFor());

        assertTrue(called.await(10L, TimeUnit.SECONDS), "WINCH handler not called");
    }
}
