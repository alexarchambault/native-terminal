package io.github.alexarchambault.nativeterm.internal;

/**
 * PowerShell scripts to interact with the terminal on Windows
 */
public final class WindowsTermScripts {

    private WindowsTermScripts() {}

    // adapted from https://github.com/rprichard/winpty/blob/7e59fe2d09adf0fa2aa606492e7ca98efbc5184e/misc/ConinMode.ps1
    /**
     * Script to enable ANSI output
     */
    public static String enableAnsiScript =
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
            "$signature = @'\n" +
            "[DllImport(\"kernel32.dll\", SetLastError = true)]\n" +
            "public static extern IntPtr GetStdHandle(int nStdHandle);\n" +
            "[DllImport(\"kernel32.dll\", SetLastError = true)]\n" +
            "public static extern uint GetConsoleMode(\n" +
            "    IntPtr hConsoleHandle,\n" +
            "    out uint lpMode);\n" +
            "[DllImport(\"kernel32.dll\", SetLastError = true)]\n" +
            "public static extern uint SetConsoleMode(\n" +
            "    IntPtr hConsoleHandle,\n" +
            "    uint dwMode);\n" +
            "public const int STD_ERROR_HANDLE = -12;\n" +
            "public const int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;\n" +
            "'@\n" +
            "\n" +
            "$WinAPI = Add-Type -MemberDefinition $signature `\n" +
            "    -Name WinAPI -Namespace ConinModeScript `\n" +
            "    -PassThru\n" +
            "\n" +
            "$handle = $WinAPI::GetStdHandle($WinAPI::STD_ERROR_HANDLE)\n" +
            "$mode = 0\n" +
            "$ret = $WinAPI::GetConsoleMode($handle, [ref]$mode)\n" +
            "if ($ret -eq 0) {\n" +
            "    Write-Host \"Error: GetConsoleMode failed (is stderr a console?)\"\n" +
            "} else {\n" +
            "    $ret = $WinAPI::SetConsoleMode($handle, $mode -bor $WinAPI::ENABLE_VIRTUAL_TERMINAL_PROCESSING)\n" +
            "    if ($ret -eq 0) {\n" +
            "        Write-Host \"false\"\n" +
            "    } else {\n" +
            "        Write-Host \"true\"\n" +
            "    }\n" +
            "}\n";

    /**
     * Script to get the terminal size
     */
    public static String getConsoleDimScript =
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
            "$signature = @\"\n" +
            "using System;\n" +
            "using System.Runtime.InteropServices;\n" +
            "\n" +
            "public class Kernel32\n" +
            "{\n" +
            "    [DllImport(\"kernel32.dll\", SetLastError = true)]\n" +
            "    public static extern bool GetConsoleScreenBufferInfo(IntPtr hConsoleOutput, out CONSOLE_SCREEN_BUFFER_INFO lpConsoleScreenBufferInfo);\n" +
            "\n" +
            "    [DllImport(\"kernel32.dll\", SetLastError = true)]\n" +
            "    public static extern IntPtr GetStdHandle(int nStdHandle);\n" +
            "\n" +
            "    public const int STD_ERROR_HANDLE = -12;\n" +
            "\n" +
            "    [StructLayout(LayoutKind.Sequential)]\n" +
            "    public struct COORD\n" +
            "    {\n" +
            "        public short X;\n" +
            "        public short Y;\n" +
            "    }\n" +
            "\n" +
            "    [StructLayout(LayoutKind.Sequential)]\n" +
            "    public struct SMALL_RECT\n" +
            "    {\n" +
            "        public short Left;\n" +
            "        public short Top;\n" +
            "        public short Right;\n" +
            "        public short Bottom;\n" +
            "    }\n" +
            "\n" +
            "    [StructLayout(LayoutKind.Sequential)]\n" +
            "    public struct CONSOLE_SCREEN_BUFFER_INFO\n" +
            "    {\n" +
            "        public COORD dwSize;\n" +
            "        public COORD dwCursorPosition;\n" +
            "        public short wAttributes;\n" +
            "        public SMALL_RECT srWindow;\n" +
            "        public COORD dwMaximumWindowSize;\n" +
            "    }\n" +
            "}\n" +
            "\"@\n" +
            "\n" +
            "Add-Type -TypeDefinition $signature -Language CSharp\n" +
            "\n" +
            "$outputHandle = [Kernel32]::GetStdHandle([Kernel32]::STD_ERROR_HANDLE)\n" +
            "\n" +
            "$info = New-Object Kernel32+CONSOLE_SCREEN_BUFFER_INFO\n" +
            "\n" +
            "if ([Kernel32]::GetConsoleScreenBufferInfo($outputHandle, [ref]$info)) {\n" +
            "    Write-Host \"Size: $($info.srWindow.Right - $info.srWindow.Left + 1) $($info.srWindow.Bottom - $info.srWindow.Top + 1)\"\n" +
            "} else {\n" +
            "    Write-Host \"Error: Win32 error $([System.Runtime.InteropServices.Marshal]::GetLastWin32Error())\"\n" +
            "}\n";

}
