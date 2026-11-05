package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.isterminal.IsTerminal;
import io.github.alexarchambault.nativeterm.internal.UnixTerm;
import io.github.alexarchambault.nativeterm.internal.WindowsTerm;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeTerminalTests {

    private static final String versionedPrefix = "META-INF/versions/22/";

    /** Java feature version, like 8, 11, or 25 (Runtime.version() isn't available on Java 8) */
    private static int javaVersion() {
        String version = System.getProperty("java.specification.version");
        if (version.startsWith("1."))
            version = version.substring("1.".length());
        return Integer.parseInt(version);
    }

    private static URL classFileUrl(Class<?> clazz) {
        URL url = clazz.getResource(clazz.getSimpleName() + ".class");
        assertNotNull(url, "class file of " + clazz.getName());
        return url;
    }

    /**
     * Checks that the tests run against the multi-release JAR, and that its FFM classes
     * are picked on Java &gt;= 22 (and only there).
     */
    @Test
    void expectedImplementationLoaded() {
        boolean expectFfm = javaVersion() >= 22;
        for (Class<?> clazz : new Class<?>[] {UnixTerm.class, WindowsTerm.class}) {
            URL url = classFileUrl(clazz);
            assertEquals("jar", url.getProtocol(), "tests should run against the JAR, got " + url);
            assertEquals(
                    expectFfm,
                    url.toString().contains("!/" + versionedPrefix),
                    "unexpected implementation on Java " + System.getProperty("java.version") + ": " + url);
        }

        // FFM-only helper class, only in META-INF/versions/22
        boolean hasFfmCLibrary;
        try {
            Class.forName("io.github.alexarchambault.nativeterm.internal.CLibrary");
            hasFfmCLibrary = true;
        } catch (ClassNotFoundException e) {
            hasFfmCLibrary = false;
        }
        assertEquals(expectFfm, hasFfmCLibrary);
    }

    @Test
    void setupAnsi() throws Exception {
        // only does something on Windows, if attached to a terminal
        NativeTerminal.setupAnsi();
    }

    /** Only asserts things when attached to a terminal (which is usually not the case here). */
    @Test
    void getSize() throws Exception {
        assumeTrue(IsTerminal.isTerminal(), "not attached to a terminal");
        TerminalSize size = NativeTerminal.getSize();
        assertNotNull(size);
        assertTrue(size.getWidth() > 0, "width: " + size);
        assertTrue(size.getHeight() > 0, "height: " + size);
    }
}
