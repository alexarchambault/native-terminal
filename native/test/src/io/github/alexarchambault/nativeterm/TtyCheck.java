package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.isterminal.IsTerminal;
import io.github.alexarchambault.nativeterm.internal.UnixTerm;

/**
 * Checks that {@link NativeTerminal#getSize()} works when attached to a terminal.
 *
 * <p>Meant to be run with {@code ./mill native.test.runMain io.github.alexarchambault.nativeterm.TtyCheck},
 * from a terminal (Mill pipes the output of tests, so tests never see a terminal).
 *
 * <p>Usage: {@code TtyCheck [expectedWidth expectedHeight]}
 */
public final class TtyCheck {
    private TtyCheck() {}

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        System.out.println("Implementation: " + UnixTerm.class.getResource("UnixTerm.class"));

        if (!IsTerminal.isTerminal()) {
            System.err.println("Error: not attached to a terminal");
            System.exit(1);
        }

        NativeTerminal.setupAnsi();
        TerminalSize size = NativeTerminal.getSize();
        System.out.println("Terminal size: " + size);

        if (size.getWidth() <= 0 || size.getHeight() <= 0) {
            System.err.println("Error: invalid terminal size " + size);
            System.exit(1);
        }

        if (args.length == 2) {
            TerminalSize expected = TerminalSize.of(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
            if (!size.equals(expected)) {
                System.err.println("Error: expected " + expected + ", got " + size);
                System.exit(1);
            }
        } else if (args.length != 0) {
            System.err.println("Usage: TtyCheck [expectedWidth expectedHeight]");
            System.exit(2);
        }
    }
}
