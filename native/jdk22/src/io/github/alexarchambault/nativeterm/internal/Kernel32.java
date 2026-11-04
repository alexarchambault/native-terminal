// Initially adapted from https://github.com/jline/jline3/blob/114e9a8f86102245ed9e2e642603f97e11ac962b/terminal-ffm/src/main/java/org/jline/terminal/impl/ffm/Kernel32.java

/*
 * Copyright (c) 2009-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package io.github.alexarchambault.nativeterm.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

@SuppressWarnings({"restricted"})
final class Kernel32 {

    static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x00001000;

    static final long INVALID_HANDLE_VALUE = -1;
    static final int STD_INPUT_HANDLE = -10;
    static final int STD_OUTPUT_HANDLE = -11;
    static final int STD_ERROR_HANDLE = -12;

    static java.lang.foreign.MemorySegment GetStdHandle(int nStdHandle) {
        MethodHandle mh$ = requireNonNull(GetStdHandle$MH, "GetStdHandle");
        try {
            return (java.lang.foreign.MemorySegment) mh$.invokeExact(nStdHandle);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * @param lpBuffer buffer receiving the message, at least {@code nSize} WCHARs (so {@code 2 * nSize} bytes) long
     * @param nSize size of {@code lpBuffer}, in WCHARs
     * @return the number of WCHARs written to {@code lpBuffer}, excluding the terminating null character, or 0 on failure
     */
    static int FormatMessageW(
            int dwMessageId,
            MemorySegment lpBuffer,
            int nSize) {
        MethodHandle mh$ = requireNonNull(FormatMessageW$MH, "FormatMessageW");
        try {
            return (int) mh$.invokeExact(
                    Kernel32.FORMAT_MESSAGE_FROM_SYSTEM,
                    MemorySegment.NULL,
                    dwMessageId,
                    0,
                    lpBuffer,
                    nSize,
                    MemorySegment.NULL);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    static int SetConsoleMode(java.lang.foreign.MemorySegment hConsoleHandle, int dwMode) {
        MethodHandle mh$ = requireNonNull(SetConsoleMode$MH, "SetConsoleMode");
        try {
            return (int) mh$.invokeExact(hConsoleHandle, dwMode);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    static int GetConsoleMode(
            java.lang.foreign.MemorySegment hConsoleHandle, java.lang.foreign.MemorySegment lpMode) {
        MethodHandle mh$ = requireNonNull(GetConsoleMode$MH, "GetConsoleMode");
        try {
            return (int) mh$.invokeExact(hConsoleHandle, lpMode);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    static int GetConsoleScreenBufferInfo(
            MemorySegment hConsoleOutput, CONSOLE_SCREEN_BUFFER_INFO lpConsoleScreenBufferInfo) {
        MethodHandle mh$ = requireNonNull(GetConsoleScreenBufferInfo$MH, "GetConsoleScreenBufferInfo");
        try {
            return (int) mh$.invokeExact(hConsoleOutput, lpConsoleScreenBufferInfo.seg);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    static int GetLastError() {
        MethodHandle mh$ = requireNonNull(GetLastError$MH, "GetLastError");
        try {
            return (int) mh$.invokeExact();
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    static String getLastErrorMessage() {
        int errorCode = GetLastError();
        return getErrorMessage(errorCode);
    }

    static String getErrorMessage(int errorCode) {
        int bufferSize = 512; // in WCHARs
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(ValueLayout.JAVA_CHAR, bufferSize);
            int len = FormatMessageW(errorCode, data, bufferSize);
            if (len <= 0)
                return "Error code " + errorCode;
            len = Math.min(len, bufferSize);
            byte[] bytes = data.asSlice(0, 2L * len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_16LE).trim();
        }
    }

    private static final java.lang.foreign.SymbolLookup SYMBOL_LOOKUP;

    static {
        System.loadLibrary("Kernel32");
        SYMBOL_LOOKUP = java.lang.foreign.SymbolLookup.loaderLookup();
    }

    static MethodHandle downcallHandle(String name, java.lang.foreign.FunctionDescriptor fdesc) {
        return SYMBOL_LOOKUP
                .find(name)
                .map(addr -> java.lang.foreign.Linker.nativeLinker().downcallHandle(addr, fdesc))
                .orElse(null);
    }

    static final java.lang.foreign.ValueLayout.OfShort C_SHORT$LAYOUT = java.lang.foreign.ValueLayout.JAVA_SHORT;
    static final java.lang.foreign.ValueLayout.OfShort C_WORD$LAYOUT = java.lang.foreign.ValueLayout.JAVA_SHORT;
    static final java.lang.foreign.ValueLayout.OfInt C_INT$LAYOUT = java.lang.foreign.ValueLayout.JAVA_INT;
    static final java.lang.foreign.AddressLayout C_POINTER$LAYOUT = java.lang.foreign.ValueLayout.ADDRESS;

    static final MethodHandle GetStdHandle$MH =
            downcallHandle("GetStdHandle", java.lang.foreign.FunctionDescriptor.of(C_POINTER$LAYOUT, C_INT$LAYOUT));
    static final MethodHandle FormatMessageW$MH = downcallHandle(
            "FormatMessageW",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT,
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_INT$LAYOUT,
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT));
    static final MethodHandle SetConsoleMode$MH = downcallHandle(
            "SetConsoleMode", java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_INT$LAYOUT));
    static final MethodHandle GetConsoleMode$MH = downcallHandle(
            "GetConsoleMode",
            java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_POINTER$LAYOUT));

    static final MethodHandle GetConsoleScreenBufferInfo$MH = downcallHandle(
            "GetConsoleScreenBufferInfo",
            java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_POINTER$LAYOUT));

    static final MethodHandle GetLastError$MH =
            downcallHandle("GetLastError", java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT));

    static final class CONSOLE_SCREEN_BUFFER_INFO {
        static final java.lang.foreign.GroupLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                COORD.LAYOUT.withName("dwSize"),
                COORD.LAYOUT.withName("dwCursorPosition"),
                C_WORD$LAYOUT.withName("wAttributes"),
                SMALL_RECT.LAYOUT.withName("srWindow"),
                COORD.LAYOUT.withName("dwMaximumWindowSize"));
        static final long srWindow$OFFSET =
                LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("srWindow"));

        private final java.lang.foreign.MemorySegment seg;

        CONSOLE_SCREEN_BUFFER_INFO() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        CONSOLE_SCREEN_BUFFER_INFO(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        CONSOLE_SCREEN_BUFFER_INFO(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public SMALL_RECT window() {
            return new SMALL_RECT(seg, srWindow$OFFSET);
        }

        public int windowWidth() {
            return this.window().width() + 1;
        }

        public int windowHeight() {
            return this.window().height() + 1;
        }
    }

    static final class COORD {
        static final java.lang.foreign.GroupLayout LAYOUT =
                java.lang.foreign.MemoryLayout.structLayout(C_SHORT$LAYOUT.withName("x"), C_SHORT$LAYOUT.withName("y"));
    }

    static final class SMALL_RECT {
        static final java.lang.foreign.GroupLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                C_SHORT$LAYOUT.withName("Left"),
                C_SHORT$LAYOUT.withName("Top"),
                C_SHORT$LAYOUT.withName("Right"),
                C_SHORT$LAYOUT.withName("Bottom"));
        static final VarHandle Left$VH = varHandle(LAYOUT, "Left");
        static final VarHandle Top$VH = varHandle(LAYOUT, "Top");
        static final VarHandle Right$VH = varHandle(LAYOUT, "Right");
        static final VarHandle Bottom$VH = varHandle(LAYOUT, "Bottom");

        private final java.lang.foreign.MemorySegment seg;

        public SMALL_RECT(java.lang.foreign.MemorySegment seg, long offset) {
            this(seg.asSlice(offset, LAYOUT.byteSize()));
        }

        public SMALL_RECT(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public short left() {
            return (short) Left$VH.get(seg);
        }

        public short top() {
            return (short) Top$VH.get(seg);
        }

        public short right() {
            return (short) Right$VH.get(seg);
        }

        public short bottom() {
            return (short) Bottom$VH.get(seg);
        }

        public short width() {
            return (short) (this.right() - this.left());
        }

        public short height() {
            return (short) (this.bottom() - this.top());
        }

    }

    static VarHandle lookupVarHandle(MemoryLayout layout, MemoryLayout.PathElement... element) {
        VarHandle h = layout.varHandle(element);

        // the last parameter of the VarHandle is additional offset, hardcode zero:
        h = MethodHandles.insertCoordinates(h, h.coordinateTypes().size() - 1, 0L);

        return h;
    }

    static VarHandle varHandle(java.lang.foreign.MemoryLayout layout, String name) {
        return lookupVarHandle(
                layout, java.lang.foreign.MemoryLayout.PathElement.groupElement(name));
    }

    static <T> T requireNonNull(T obj, String symbolName) {
        if (obj == null) {
            throw new UnsatisfiedLinkError("unresolved symbol: " + symbolName);
        }
        return obj;
    }
}
