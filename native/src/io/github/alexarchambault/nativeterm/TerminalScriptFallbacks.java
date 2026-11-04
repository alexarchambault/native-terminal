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
     * @throws InterruptedException if the {@code tput} or PowerShell sub-process gets interrupted
     * @throws IOException if anything goes wrong (sub-process failure, unexpected output, etc.)
     * @throws IllegalArgumentException if the current process isn't attached to a terminal
     * @return the terminal size
     */
    public static TerminalSize getSize() throws InterruptedException, IOException {
        if (!IsTerminal.isTerminal())
            throw new IllegalArgumentException("Cannot get terminal size without a terminal");
        if (isWindows) {
            String output = ScriptRunner.runPowerShellScript(WindowsTermScripts.getConsoleDimScript);
            String lastLine = lastLine(output, "gets terminal size");
            String prefix = "Size: ";
            if (lastLine.startsWith(prefix)) {
                String[] split = lastLine.substring(prefix.length()).trim().split("\\s+");
                if (split.length == 2) {
                    try {
                        return new TerminalSize(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                    } catch (NumberFormatException e) {
                        throw new IOException("Malformed size in output of PowerShell script that gets terminal size: '" + lastLine + "'", e);
                    }
                }
                throw new IOException("Malformed size in output of PowerShell script that gets terminal size: '" + lastLine + "'");
            }
            throw new IOException("Invalid output from PowerShell script that gets terminal size: '" + output.trim() + "'");
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

    /**
     * Returns the last non-empty line of a PowerShell script output
     *
     * @throws IOException if the output is empty, or if its last line is an error reported by the script
     */
    private static String lastLine(String output, String scriptDescription) throws IOException {
        String[] lines = output.trim().split("\\r?\\n");
        String lastLine = lines[lines.length - 1].trim();
        if (lastLine.isEmpty())
            throw new IOException("Empty output from PowerShell script that " + scriptDescription);
        if (lastLine.startsWith("Error:"))
            throw new IOException("PowerShell script that " + scriptDescription + " failed: " + lastLine);
        return lastLine;
    }
}
