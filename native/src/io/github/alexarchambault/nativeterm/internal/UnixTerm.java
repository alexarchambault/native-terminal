package io.github.alexarchambault.nativeterm.internal;

import io.github.alexarchambault.nativeterm.TerminalSize;
import org.jline.nativ.CLibrary;

import java.io.IOException;

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
     * @throws IOException if the file descriptor isn't a terminal, or if its size can't be read
     */
    public static TerminalSize getSize(boolean useStdout) throws IOException {
        WinSize sz = new WinSize();
        int fd = useStdout ? 1 : 2;
        // jline-native returns the raw ioctl result (-1 on failure), errno isn't exposed
        int res = ioctl(fd, CLibrary.TIOCGWINSZ, sz);
        if (res != 0)
            throw new IOException(
                    "ioctl(TIOCGWINSZ) failed on file descriptor " + fd + " (returned " + res +
                            "), it is probably not a terminal");
        int width = Short.toUnsignedInt(sz.ws_col);
        int height = Short.toUnsignedInt(sz.ws_row);
        if (width == 0 && height == 0)
            throw new IOException("ioctl(TIOCGWINSZ) returned a 0x0 size on file descriptor " + fd);
        return TerminalSize.of(width, height);
    }

}
