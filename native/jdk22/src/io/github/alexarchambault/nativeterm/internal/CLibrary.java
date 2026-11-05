// Initially adapted from https://github.com/jline/jline3/blob/114e9a8f86102245ed9e2e642603f97e11ac962b/terminal-ffm/src/main/java/org/jline/terminal/impl/ffm/CLibrary.java

/*
 * Copyright (c) 2022-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package io.github.alexarchambault.nativeterm.internal;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.foreign.MemoryLayout.PathElement;

import java.io.IOException;

import io.github.alexarchambault.nativeterm.TerminalSize;

@SuppressWarnings("restricted")
class CLibrary {

    static VarHandle lookupVarHandle(PathElement... element) {
        VarHandle h = winsize.LAYOUT.varHandle(element);

        // the last parameter of the VarHandle is additional offset, hardcode zero:
        h = MethodHandles.insertCoordinates(h, h.coordinateTypes().size() - 1, 0L);

        return h;
    }

    // Window sizes.
    // @see <a href="http://man7.org/linux/man-pages/man4/tty_ioctl.4.html">IOCTL_TTY(2) man-page</a>
    static class winsize {
        static final GroupLayout LAYOUT;
        private static final VarHandle ws_col;
        private static final VarHandle ws_row;

        static {
            LAYOUT = MemoryLayout.structLayout(
                    ValueLayout.JAVA_SHORT.withName("ws_row"),
                    ValueLayout.JAVA_SHORT.withName("ws_col"),
                    ValueLayout.JAVA_SHORT,
                    ValueLayout.JAVA_SHORT);
            ws_row = lookupVarHandle(MemoryLayout.PathElement.groupElement("ws_row"));
            ws_col = lookupVarHandle(MemoryLayout.PathElement.groupElement("ws_col"));
        }

        private final java.lang.foreign.MemorySegment seg;

        winsize(Arena arena) {
            seg = arena.allocate(LAYOUT);
        }

        java.lang.foreign.MemorySegment segment() {
            return seg;
        }

        short ws_col() {
            return (short) ws_col.get(seg);
        }

        short ws_row() {
            return (short) ws_row.get(seg);
        }
    }

    // Initialized lazily, so that failures surface as IOException at call time,
    // rather than as ExceptionInInitializerError / NoClassDefFoundError
    //
    // Each downcall here needs matching entries in this module's reachability-metadata.json
    // (GraalVM >= 25) and foreign-config.json (GraalVM 22 - 24),
    // for native images
    private static final class Ioctl {
        static final StructLayout CAPTURE_STATE_LAYOUT;
        static final long ERRNO_OFFSET;
        static final MethodHandle HANDLE;
        static final Throwable INIT_ERROR;

        static {
            StructLayout captureStateLayout = null;
            long errnoOffset = -1L;
            MethodHandle handle = null;
            Throwable initError = null;
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup lookup = SymbolLookup.loaderLookup().or(linker.defaultLookup());
                captureStateLayout = Linker.Option.captureStateLayout();
                errnoOffset = captureStateLayout.byteOffset(PathElement.groupElement("errno"));
                // https://man7.org/linux/man-pages/man2/ioctl.2.html
                MemorySegment ioctlAddr = lookup.find("ioctl")
                        .orElseThrow(() -> new UnsatisfiedLinkError("ioctl symbol not found"));
                handle = linker.downcallHandle(
                        ioctlAddr,
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
                        Linker.Option.firstVariadicArg(2),
                        Linker.Option.captureCallState("errno"));
            } catch (Throwable t) {
                initError = t;
                handle = null;
            }
            CAPTURE_STATE_LAYOUT = captureStateLayout;
            ERRNO_OFFSET = errnoOffset;
            HANDLE = handle;
            INIT_ERROR = initError;
        }
    }

    static TerminalSize getTerminalSize(int fd) throws IOException {
        if (TIOCGWINSZ == null)
            throw new IOException(
                    "Getting the terminal size via ioctl(TIOCGWINSZ) is not supported on " +
                            System.getProperty("os.name") + " (TIOCGWINSZ value unknown)");
        if (Ioctl.HANDLE == null)
            throw new IOException("Unable to set up a downcall handle for ioctl", Ioctl.INIT_ERROR);

        int res;
        int errno;
        int width;
        int height;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment callState = arena.allocate(Ioctl.CAPTURE_STATE_LAYOUT);
            winsize ws = new winsize(arena);
            try {
                res = (int) Ioctl.HANDLE.invokeExact(callState, fd, (long) TIOCGWINSZ, ws.segment());
            } catch (Throwable e) {
                throw new IOException("Unable to call ioctl(TIOCGWINSZ)", e);
            }
            errno = callState.get(ValueLayout.JAVA_INT, Ioctl.ERRNO_OFFSET);
            width = Short.toUnsignedInt(ws.ws_col());
            height = Short.toUnsignedInt(ws.ws_row());
        }

        if (res != 0)
            throw new IOException(
                    "ioctl(TIOCGWINSZ) failed on file descriptor " + fd + " (errno " + errno + errnoDescription(errno) + ")");
        if (width == 0 && height == 0)
            throw new IOException("ioctl(TIOCGWINSZ) returned a 0x0 size on file descriptor " + fd);
        return TerminalSize.of(width, height);
    }

    private static String errnoDescription(int errno) {
        // These values are the same on Linux, macOS, FreeBSD and Solaris
        switch (errno) {
            case 9: return ", EBADF: bad file descriptor";
            case 25: return ", ENOTTY: not a terminal";
            default: return "";
        }
    }

    // CONSTANTS

    // null on unsupported OSes
    private static final Integer TIOCGWINSZ;

    static {
        String osName = System.getProperty("os.name", "");
        if (osName.startsWith("Linux")) {
            String arch = System.getProperty("os.arch");
            boolean isMipsPpcOrSparc = arch.equals("mips")
                    || arch.equals("mips64")
                    || arch.equals("mipsel")
                    || arch.equals("mips64el")
                    || arch.startsWith("ppc")
                    || arch.startsWith("sparc");
            TIOCGWINSZ = isMipsPpcOrSparc ? 0x40087468 : 0x00005413;
        } else if (osName.startsWith("Solaris") || osName.startsWith("SunOS")) {
            int _TIOC = ('T' << 8);
            TIOCGWINSZ = (_TIOC | 104);
        } else if (osName.startsWith("Mac") || osName.startsWith("Darwin")) {
            TIOCGWINSZ = 0x40087468;
        } else if (osName.startsWith("FreeBSD")) {
            TIOCGWINSZ = 0x40087468;
        } else {
            TIOCGWINSZ = null;
        }
    }
}
