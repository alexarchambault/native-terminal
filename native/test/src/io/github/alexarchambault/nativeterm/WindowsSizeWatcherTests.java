package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.nativeterm.internal.WindowsSizeWatcher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the polling logic of WindowsSizeWatcher, with a fake terminal size source
 * (so this runs on all platforms)
 */
class WindowsSizeWatcherTests {

    @Test
    void handlersCalledOnSizeChange() throws Exception {
        AtomicReference<TerminalSize> size = new AtomicReference<>(TerminalSize.of(80, 24));
        Callable<TerminalSize> getSize = size::get;
        WindowsSizeWatcher watcher = new WindowsSizeWatcher(getSize, 10L);
        try {
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch firstCalled = new CountDownLatch(1);
            CountDownLatch secondCalled = new CountDownLatch(1);
            watcher.add(new Runnable() {
                @Override
                public void run() {
                    calls.incrementAndGet();
                    firstCalled.countDown();
                }
            });
            watcher.add(new Runnable() {
                @Override
                public void run() {
                    secondCalled.countDown();
                    throw new RuntimeException("handler failure, should be ignored");
                }
            });

            // no change, no call
            Thread.sleep(100L);
            assertEquals(0, calls.get());

            size.set(TerminalSize.of(120, 40));
            assertTrue(firstCalled.await(10L, TimeUnit.SECONDS), "first handler not called");
            assertTrue(secondCalled.await(10L, TimeUnit.SECONDS), "second handler not called");

            // still no change, no further call
            Thread.sleep(100L);
            assertEquals(1, calls.get());
        } finally {
            watcher.stop();
        }
    }

    @Test
    void sizeErrorsIgnored() throws Exception {
        AtomicReference<TerminalSize> size = new AtomicReference<>(null);
        Callable<TerminalSize> getSize = new Callable<TerminalSize>() {
            @Override
            public TerminalSize call() throws Exception {
                TerminalSize size0 = size.get();
                if (size0 == null)
                    throw new Exception("no terminal");
                return size0;
            }
        };
        WindowsSizeWatcher watcher = new WindowsSizeWatcher(getSize, 10L);
        try {
            CountDownLatch called = new CountDownLatch(1);
            watcher.add(called::countDown);
            Thread.sleep(100L);
            assertEquals(1L, called.getCount());

            size.set(TerminalSize.of(100, 30));
            assertTrue(called.await(10L, TimeUnit.SECONDS), "handler not called");
        } finally {
            watcher.stop();
        }
    }
}
