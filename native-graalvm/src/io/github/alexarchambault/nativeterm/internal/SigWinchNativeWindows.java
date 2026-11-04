package io.github.alexarchambault.nativeterm.internal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.function.Consumer;

/**
 * Voids the SigWinch class when building native images on Windows,
 * so that its sun.misc use aren't an issue there.
 */
@TargetClass(className = "io.github.alexarchambault.nativeterm.internal.SigWinch")
@Platforms(Platform.WINDOWS.class)
public final class SigWinchNativeWindows {

  @Substitute
  public static void addHandler(Runnable runnable) {
    // terminal size changes ignored for now
  }

  @Substitute
  public static void addHandler(Runnable runnable, Consumer<Throwable> onError) {
    // terminal size changes ignored for now
  }

}
