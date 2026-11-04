package io.github.alexarchambault.nativeterm.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utilities to run scripts that interact with the terminal
 */
public final class ScriptRunner {

    private ScriptRunner() {}

    /**
     * Runs a PowerShell script
     * @param script the script to run
     * @return the output of the script
     * @throws InterruptedException if the PowerShell sub-process gets interrupted
     * @throws IOException if anything goes wrong
     */
    public static String runPowerShellScript(String script) throws InterruptedException, IOException {

        String fullScript = "& {\n" +
                script +
                "\n}";

        Base64.Encoder base64 = Base64.getEncoder();
        String encodedScript = base64.encodeToString(fullScript.getBytes(StandardCharsets.UTF_16LE));

        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-EncodedCommand", encodedScript);
        builder
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);

        return runAndReadOutput(builder, "Error running powershell script");
    }

    /**
     * Runs {@code tput} in the current terminal
     * @param s the {@code tput} parameter you're interested in
     * @return the value of that parameter returned by {@code tput}, as an integer
     * @throws InterruptedException if the tput sub-process gets interrupted
     * @throws IOException if anything goes wrong
     */
    public static int runTput(String s) throws InterruptedException, IOException {
        ProcessBuilder builder = new ProcessBuilder()
                .command("tput", s)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);

        String output = runAndReadOutput(builder, "tput " + s + " failed").trim();
        try {
            return Integer.parseInt(output);
        } catch (NumberFormatException e) {
            throw new IOException("Unexpected output from tput " + s + ": '" + output + "'", e);
        }
    }

    /**
     * Starts a process, reads its standard output until EOF, and waits for it to exit
     *
     * <p>The process is only destroyed if something goes wrong (reading its output fails,
     * or the current thread gets interrupted while waiting for it): it must not be destroyed
     * when it closed its standard output but didn't exit yet, as killing it then would
     * make it exit with a non-zero code.
     *
     * @param builder the process to start, with its output redirected to a pipe
     * @param errorPrefix prefix of the exception message if the process exits with a non-zero code
     * @return the standard output of the process, with each line terminated by {@code \r\n}
     */
    private static String runAndReadOutput(ProcessBuilder builder, String errorPrefix) throws InterruptedException, IOException {
        Process proc = builder.start();
        boolean success = false;
        try {
            StringBuilder results = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    results.append(line);
                    results.append("\r\n");
                    line = reader.readLine();
                }
            }

            int exitCode = proc.waitFor();
            if (exitCode != 0)
                throw new IOException(errorPrefix + " (exit code: " + exitCode + ")");
            success = true;
            return results.toString();
        } finally {
            if (!success)
                proc.destroy();
        }
    }

}
