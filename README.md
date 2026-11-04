# native-terminal

[![Build status](https://github.com/alexarchambault/native-terminal/actions/workflows/ci.yml/badge.svg)](https://github.com/alexarchambault/native-terminal/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.alexarchambault.native-terminal/native-terminal.svg)](https://central.sonatype.com/artifact/io.github.alexarchambault.native-terminal/native-terminal)

*native-terminal* is a small Java library to setup / interact with terminals in a native fashion. It allows to
- query the terminal size, and
- on Windows, change the console mode so that it accepts ANSI escape codes.

It relies on internals of the [jline-native](https://github.com/jline/jline3/tree/master/native) library to do so, and also works from
GraalVM native images.

Compared to using [jline](https://github.com/jline/jline3), *native-terminal* only and solely calls the right
`ioctl` system calls
(or `kernel32.dll` system calls, like [`SetConsoleMode`](https://docs.microsoft.com/en-us/windows/console/setconsolemode)
or [`GetConsoleScreenBufferInfo`](https://docs.microsoft.com/en-us/windows/console/getconsolescreenbufferinfo), on Windows),
lowering the odds of something going wrong when generating or using a GraalVM native image for example.

## Installation

*native-terminal* requires Java 8 or later.

sbt:
```scala
libraryDependencies += "io.github.alexarchambault.native-terminal" % "native-terminal" % "0.0.9.1"
```

Mill:
```scala
def mvnDeps = Seq(
  mvn"io.github.alexarchambault.native-terminal:native-terminal:0.0.9.1"
)
```

Scala CLI:
```scala
//> using dep io.github.alexarchambault.native-terminal:native-terminal:0.0.9.1
```

Gradle:
```kotlin
implementation("io.github.alexarchambault.native-terminal:native-terminal:0.0.9.1")
```

Maven:
```xml
<dependency>
  <groupId>io.github.alexarchambault.native-terminal</groupId>
  <artifactId>native-terminal</artifactId>
  <version>0.0.9.1</version>
</dependency>
```

The latest version is [![Maven Central](https://img.shields.io/maven-central/v/io.github.alexarchambault.native-terminal/native-terminal.svg)](https://central.sonatype.com/artifact/io.github.alexarchambault.native-terminal/native-terminal).

## Usage

### Change terminal mode

Change the terminal mode on Windows, so that it accepts ANSI escape codes with
```java
import io.github.alexarchambault.nativeterm.NativeTerminal;

boolean success = NativeTerminal.setupAnsi();
```

A returned value of `false` means ANSI escape codes aren't supported by the Windows version you're running on.
These are supposed to be supported by Windows 10 build 10586 (Nov. 2015) onwards.

Calling this method is safe on other platforms. It simply returns true in that case.
It also returns true without changing anything on Windows, if the current process isn't
attached to a terminal (if its output is redirected for example).

On Windows, it throws an `IOException` if the current console mode can't be read.

### Get terminal size

```java
import io.github.alexarchambault.nativeterm.NativeTerminal;
import io.github.alexarchambault.nativeterm.TerminalSize;

TerminalSize size = NativeTerminal.getSize();
int width = size.getWidth();
int height = size.getHeight();
```

The size is queried for the standard output of the current process. If it isn't a terminal
(if it's redirected to a file or a pipe for example), the native calls fail, and the returned size
can be 0 × 0 rather than an exception being thrown. Check for a non-positive width or height if your
application can run with its output redirected.

If you call this method often, on Unix-es, you might want to use `TerminalSizeCache` instead.

```java
import io.github.alexarchambault.nativeterm.TerminalSize;
import io.github.alexarchambault.nativeterm.TerminalSizeCache;

// Create a single instance, and re-use it throughout your application
TerminalSizeCache cache = new TerminalSizeCache();

TerminalSize size = cache.getSize();
int width = size.getWidth();
int height = size.getHeight();
```

On Unix-es, creating a `TerminalSizeCache` registers a handler for the WINCH signal, that invalidates
the cached terminal size. That way, the terminal size is queried only when the terminal size
changes, or if it's not cached yet (or the cached value has been invalidated).
If the terminal size didn't change, the cached value is returned, and no unnecessary system call
querying the terminal size is made.

On Windows, this class just calls `NativeTerminal.getSize()` upon every call to
`TerminalSizeCache#getSize()`.

### Fallbacks running external processes

`TerminalScriptFallbacks` has the same methods as `NativeTerminal`, but doesn't make any native call
from the JVM. It runs external processes instead: `tput` on Linux / macOS, and PowerShell scripts
calling `kernel32.dll` methods on Windows. This is much slower, but can be used if native calls
aren't an option in your environment.

```java
import io.github.alexarchambault.nativeterm.TerminalScriptFallbacks;
import io.github.alexarchambault.nativeterm.TerminalSize;

boolean success = TerminalScriptFallbacks.setupAnsi();
TerminalSize size = TerminalScriptFallbacks.getSize();
```

`TerminalScriptFallbacks.getSize()` throws an `IllegalArgumentException` if the current process
isn't attached to a terminal.

## JDK 22 and later

The `native-terminal` JAR is a multi-release JAR. On JDK 22 and later, it doesn't use
jline-native, and calls `ioctl` or `kernel32.dll` methods via the
[Foreign Function & Memory API](https://openjdk.org/jeps/454) instead.

The JVM prints a warning the first time such native calls are made, unless native access is enabled. Pass
```text
--enable-native-access=ALL-UNNAMED
```
to `java` to enable it (or the name of your module rather than `ALL-UNNAMED`, if you use the module system).
For an executable JAR, you can also add `Enable-Native-Access: ALL-UNNAMED` to its manifest.

Note that from JDK 24 onwards, loading JNI libraries such as jline-native's also prints such a warning
if native access isn't enabled.

## Other modules

### `native-terminal-no-ffm`

`native-terminal-no-ffm` has the same API as `native-terminal`, but isn't a multi-release JAR:
it always relies on jline-native (via JNI), whatever the JDK version. Use it instead of `native-terminal`
if you'd rather not have the [FFM-based implementation](#jdk-22-and-later) be picked on JDK 22 and later.
```scala
libraryDependencies += "io.github.alexarchambault.native-terminal" % "native-terminal-no-ffm" % "0.0.9.1"
```

### `native-terminal-graalvm`

`native-terminal-graalvm` provides a GraalVM native image `Feature`, that registers the jline-native classes
for JNI access, and embeds the jline-native library of the target platform in the image. It's annotated
with `@AutomaticFeature`, so that adding this module to the class path of `native-image` is enough to enable it.
```scala
libraryDependencies += "io.github.alexarchambault.native-terminal" % "native-terminal-graalvm" % "0.0.9.1"
```

Note that, unlike the other modules, this module is only licensed under the GNU GPL version 2 license
with the Classpath Exception (see [License](#license)).

## Development

*native-terminal* is built with [Mill](https://mill-build.org). The `./mill` launcher at the root
of the repository downloads the right Mill version.

Compile all modules and publish them locally (in `~/.ivy2/local`) with
```text
$ ./mill __.publishLocal
```

Check binary compatibility with the former releases with
```text
$ ./mill __.mimaReportBinaryIssues
```

The published version is computed from git tags. Pushing a `v*` tag (like `v0.0.10`) makes the CI
publish that version to Maven Central. Pushes to `main` publish snapshots.

## License

All files in this repository, except `NativeImageFeature.java`, can be used either under the
Apache 2.0 license, or the GNU GPL version 2 license with the Classpath Exception, at your convenience.

The `NativeImageFeature.java` file, originally based on a GNU GPL version 2 only file (with the
Classpath Exception), is licensed only under the GNU GPL version 2 license with the Classpath Exception.

See [LICENSE](LICENSE) for the full terms.

