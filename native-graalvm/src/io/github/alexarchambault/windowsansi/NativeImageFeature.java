package io.github.alexarchambault.windowsansi;

// Originally adapted from https://github.com/oracle/graal/blob/a542ddab3aff34798a17f67845e38491f35f1998/substratevm/src/com.oracle.svm.thirdparty/src/com/oracle/svm/thirdparty/jline/JLineFeature.java
// whose copyright is reproduced below.

/*
 *
 * Original copyright:
 *
 *
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticFeature
final class NativeImageFeature implements Feature {

    /** List of the classes that access the JNI library. */
    private static final List<String> JNI_CLASS_NAMES = Arrays.asList(
            "org.jline.nativ.CLibrary",
            "org.jline.nativ.CLibrary$WinSize",
            "org.jline.nativ.CLibrary$Termios");
    private static final List<String> WINDOWS_JNI_CLASS_NAMES = Arrays.asList(
            "org.jline.nativ.Kernel32",
            "org.jline.nativ.Kernel32$SMALL_RECT",
            "org.jline.nativ.Kernel32$COORD",
            "org.jline.nativ.Kernel32$CONSOLE_SCREEN_BUFFER_INFO",
            "org.jline.nativ.Kernel32$CHAR_INFO",
            "org.jline.nativ.Kernel32$KEY_EVENT_RECORD",
            "org.jline.nativ.Kernel32$MOUSE_EVENT_RECORD",
            "org.jline.nativ.Kernel32$WINDOW_BUFFER_SIZE_RECORD",
            "org.jline.nativ.Kernel32$FOCUS_EVENT_RECORD",
            "org.jline.nativ.Kernel32$MENU_EVENT_RECORD",
            "org.jline.nativ.Kernel32$INPUT_RECORD");

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        /*
         * Each listed class has a native method named "init" that initializes all declared
         * fields of the class using JNI. So when the "init" method gets reachable (which means
         * the class initializer got reachable), we need to register all fields for JNI access.
         */
        JNI_CLASS_NAMES.stream()
                .map(access::findClassByName)
                .filter(Objects::nonNull)
                .map(jniClass -> ReflectionUtil.lookupMethod(jniClass, "init"))
                .forEach(initMethod -> access.registerReachabilityHandler(a -> registerJNIFields(initMethod), initMethod));

        if (Platform.includedIn(Platform.WINDOWS.class)) {
            WINDOWS_JNI_CLASS_NAMES.stream()
                    .map(access::findClassByName)
                    .filter(Objects::nonNull)
                    .map(jniClass -> ReflectionUtil.lookupMethod(jniClass, "init"))
                    .forEach(initMethod -> access.registerReachabilityHandler(a -> registerJNIFields(initMethod), initMethod));
        }
    }

    private final AtomicBoolean resourceRegistered = new AtomicBoolean();

    private void registerJNIFields(Method initMethod) {
        Class<?> jniClass = initMethod.getDeclaringClass();
        JNIRuntimeAccess.register(jniClass.getDeclaredFields());

        if (!resourceRegistered.getAndSet(true)) {
            /* The native library that is included as a resource in the .jar file. */
            String resource = null;
            if (Platform.includedIn(Platform.WINDOWS_AMD64.class))
                resource = "Windows/x86_64/jlinenative.dll";
            else if (Platform.includedIn(Platform.WINDOWS_AARCH64.class))
                resource = "Windows/arm64/jlinenative.dll";
            else if (Platform.includedIn(Platform.LINUX_AMD64.class))
                resource = "Linux/x86_64/libjlinenative.so";
            else if (Platform.includedIn(Platform.LINUX_AARCH64.class))
                resource = "Linux/arm64/libjlinenative.so";
            else if (Platform.includedIn(Platform.DARWIN_AMD64.class))
                resource = "Mac/x86_64/libjlinenative.jnilib";
            else if (Platform.includedIn(Platform.DARWIN_AARCH64.class))
                resource = "Mac/arm64/libjlinenative.jnilib";

            if (resource != null) {
                resource = "org/jline/nativ/" + resource;
                InputStream is = jniClass.getClassLoader().getResourceAsStream(resource);
                if (is == null)
                    throw new RuntimeException("Could not find resource " + resource);
                Resources.registerResource(resource, is);
            }
        }
    }
}
