package io.github.alexarchambault.nativeterm.internal.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.function.Consumer;

/**
 * Replaces the SigWinch methods when building native images on Windows,
 * so that its sun.misc use aren't an issue there.
 *
 * Terminal size changes are detected by polling there anyway (see WindowsSizeWatcher).
 */
@TargetClass(className = "io.github.alexarchambault.nativeterm.internal.SigWinch")
@Platforms(Platform.WINDOWS.class)
public final class SigWinchNativeWindows {

  @Substitute
  public static void addHandler(Runnable runnable) {
    WindowsSizeWatcher.addHandler(runnable);
  }

  @Substitute
  public static void addHandler(Runnable runnable, Consumer<Throwable> onError) {
    WindowsSizeWatcher.addHandler(runnable);
  }

}
