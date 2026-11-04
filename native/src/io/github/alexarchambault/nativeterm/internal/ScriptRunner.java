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

        Process proc = builder.start();

        StringBuilder results = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        try {
            String line = reader.readLine();
            while (line != null) {
                results.append(line);
                results.append("\r\n");
                line = reader.readLine();
            }
        } finally {
            proc.destroy();
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int retCode = proc.waitFor();
        if (retCode != 0)
            throw new IOException("Error running powershell script (return code: " + retCode + ")");
        return results.toString();
    }

    /**
     * Runs {@code tput} in the current terminal
     * @param s the {@code tput} parameter you're interested in
     * @return the value of that parameter returned by {@code tput}, as an integer
     * @throws InterruptedException if the tput sub-process gets interrupted
     * @throws IOException if anything goes wrong
     */
    public static int runTput(String s) throws InterruptedException, IOException {
        Process proc = new ProcessBuilder()
                .command("tput", s)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        StringBuilder results = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        try {
            String line = reader.readLine();
            while (line != null) {
                results.append(line);
                results.append("\r\n");
                line = reader.readLine();
            }
        } finally {
            proc.destroy();
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int exitCode = proc.waitFor();
        if (exitCode != 0) throw new IOException("tput " + s + " failed (exit code: " + exitCode + ")");
        return Integer.parseInt(results.toString().trim());
    }

}
