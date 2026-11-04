package io.github.alexarchambault.nativeterm;

import io.github.alexarchambault.isterminal.IsTerminal;
import io.github.alexarchambault.nativeterm.internal.ScriptRunner;
import io.github.alexarchambault.nativeterm.internal.WindowsTermScripts;

import java.io.IOException;

import static io.github.alexarchambault.nativeterm.NativeTerminal.isWindows;

/**
 * Methods to query the terminal size and enable ANSI output in it like in {@link NativeTerminal}
 */
public final class TerminalScriptFallbacks {

    private TerminalScriptFallbacks() {}

    /**
     * Gets the terminal size
     *
     * <p>This runs {@code tput} via an external process on Linux / Mac, and runs
     * a PowerShell script that calls {@code kernel32.dll} methods on Windows
     *
     * @throws InterruptedException if the PowerShell sub-process gets interrupted
     * @throws IOException if anything goes wrong
     * @return the terminal size
     */
    public static TerminalSize getSize() throws InterruptedException, IOException {
        if (!IsTerminal.isTerminal())
            throw new IllegalArgumentException("Cannot get terminal size without a terminal");
        if (isWindows) {
            String output = ScriptRunner.runPowerShellScript(WindowsTermScripts.getConsoleDimScript).trim();
            String[] lines = output.split("\\r?\\n");
            String lastLine = lines[lines.length - 1];
            if (lastLine.startsWith("Error:"))
                throw new IOException(lastLine);
            if (lastLine.startsWith("Size: ")) {
                lastLine = lastLine.substring("Size: ".length());
                String[] split = lastLine.split("\\s+");
                return new TerminalSize(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
            }
            throw new IOException("Invalid output from PowerShell script that gets terminal size: '" + output + "'");
        }
        else {
            return new TerminalSize(ScriptRunner.runTput("cols"), ScriptRunner.runTput("lines"));
        }
    }

    /**
     * Enables ANSI terminal output (only needed on Windows)
     *
     * <p>It is safe to call this method on non-Windows systems. It simply
     * returns true in that case.
     *
     * <p>Under-the-hood, this calls a {@code kernel32.dll} method, via a PowerShell script,
     * run in an external process.
     *
     * @throws IOException if anything goes wrong
     * @throws InterruptedException if the PowerShell sub-process gets interrupted
     * @return Whether ANSI output is enabled
     */
    public static boolean setupAnsi() throws InterruptedException, IOException {
        if (isWindows && IsTerminal.isTerminal()) {
            String output = ScriptRunner.runPowerShellScript(WindowsTermScripts.enableAnsiScript);
            String[] lines = output.split("\\r?\\n");
            String lastLine = lines[lines.length - 1];
            return Boolean.parseBoolean(lastLine);
        }
        return true;
    }
}
