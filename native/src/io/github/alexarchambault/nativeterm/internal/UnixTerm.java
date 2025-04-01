package io.github.alexarchambault.nativeterm.internal;

import io.github.alexarchambault.nativeterm.TerminalSize;
import org.jline.nativ.CLibrary;

import static org.jline.nativ.CLibrary.*;

/**
 * Utilities to get the terminal size on Linux / Mac
 */
public final class UnixTerm {

    private UnixTerm() {}

    /**
     * Gets the terminal size on Linux / Mac
     * @param useStdout whether to use stdout or stderr
     * @return the terminal size
     */
    public static TerminalSize getSize(boolean useStdout) {
        WinSize sz = new WinSize();
        int fd = useStdout ? 1 : 2;
        ioctl(fd, CLibrary.TIOCGWINSZ, sz);
        return TerminalSize.of(sz.ws_col, sz.ws_row);
    }

}
