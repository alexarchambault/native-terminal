package io.github.alexarchambault.nativeterm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the FFM downcalls registered for GraalVM native images in the
 * META-INF/native-image resources of the native-terminal JAR.
 *
 * <p>Downcalls that aren't registered there make native images fail at runtime, with a
 * MissingForeignRegistrationError (GraalVM &gt;= 25) or an UnregisteredForeignStubException
 * (GraalVM 22 - 24).
 */
class NativeImageMetadataTests {

    private static final String metadataDir =
            "META-INF/native-image/io.github.alexarchambault.native-terminal/native-terminal/";

    /**
     * Linker options of the downcall handles that have some, by handle name (see {@link #downcalls}).
     *
     * <p>These can't be inferred from the handles themselves. Handles capturing the call state
     * get an extra leading MemorySegment parameter for it, that is stripped from their descriptor.
     */
    private static final Map<String, Map<String, String>> downcallOptions = new HashMap<>();

    static {
        Map<String, String> captureCallState = Collections.singletonMap("captureCallState", "true");
        Map<String, String> ioctlOptions = new TreeMap<>(captureCallState);
        ioctlOptions.put("firstVariadicArg", "2");
        downcallOptions.put("CLibrary$Ioctl.HANDLE", ioctlOptions);
        downcallOptions.put("Kernel32.SetConsoleMode$MH", captureCallState);
        downcallOptions.put("Kernel32.GetConsoleMode$MH", captureCallState);
        downcallOptions.put("Kernel32.GetConsoleScreenBufferInfo$MH", captureCallState);
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    /** Java feature version, like 8, 11, or 25 (Runtime.version() isn't available on Java 8) */
    private static int javaVersion() {
        String version = System.getProperty("java.specification.version");
        if (version.startsWith("1."))
            version = version.substring("1.".length());
        return Integer.parseInt(version);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static JsonNode readMetadata(String name) throws IOException {
        String path = metadataDir + name;
        URL url = NativeImageMetadataTests.class.getClassLoader().getResource(path);
        assertNotNull(url, path + " not found");
        assertEquals("jar", url.getProtocol(), "tests should run against the JAR, got " + url);
        try (InputStream is = url.openStream()) {
            return mapper.readTree(is);
        }
    }

    /** Downcall entry, like {@code (jint, jlong, void*)jint captureCallState=true firstVariadicArg=2} */
    private static String entry(String descriptor, Map<String, String> options) {
        StringBuilder b = new StringBuilder(descriptor.replaceAll("\\s+", "").replace(",", ", "));
        for (Map.Entry<String, String> e : new TreeMap<>(options).entrySet())
            b.append(' ').append(e.getKey()).append('=').append(e.getValue());
        return b.toString();
    }

    private static String entry(String descriptor, JsonNode options) {
        Map<String, String> map = new HashMap<>();
        if (options != null)
            for (Map.Entry<String, JsonNode> e : options.properties())
                map.put(e.getKey(), e.getValue().asText());
        return entry(descriptor, map);
    }

    /** Downcalls of reachability-metadata.json (read by GraalVM &gt;= 25) */
    private static List<String> reachabilityMetadataDowncalls() throws IOException {
        JsonNode downcalls = readMetadata("reachability-metadata.json").path("foreign").path("downcalls");
        assertTrue(downcalls.isArray(), "no foreign.downcalls array in reachability-metadata.json");
        List<String> entries = new ArrayList<>();
        for (JsonNode downcall : downcalls) {
            List<String> params = new ArrayList<>();
            for (JsonNode param : downcall.path("parameterTypes"))
                params.add(param.asText());
            String descriptor = "(" + String.join(", ", params) + ")" + downcall.path("returnType").asText();
            entries.add(entry(descriptor, downcall.get("options")));
        }
        return entries;
    }

    /** Downcalls of foreign-config.json (read by GraalVM 22 - 24) */
    private static List<String> foreignConfigDowncalls() throws IOException {
        JsonNode downcalls = readMetadata("foreign-config.json").path("downcalls");
        assertTrue(downcalls.isArray(), "no downcalls array in foreign-config.json");
        List<String> entries = new ArrayList<>();
        for (JsonNode downcall : downcalls)
            entries.add(entry(downcall.path("descriptor").asText(), downcall.get("options")));
        return entries;
    }

    private static String nativeType(Class<?> clazz) {
        if (clazz == int.class)
            return "jint";
        if (clazz == long.class)
            return "jlong";
        if (clazz == short.class)
            return "jshort";
        if (clazz == byte.class)
            return "jbyte";
        if (clazz == void.class)
            return "void";
        if (clazz.getName().equals("java.lang.foreign.MemorySegment"))
            return "void*";
        throw new IllegalArgumentException("Unsupported downcall type: " + clazz);
    }

    /**
     * Downcall entries of the MethodHandle static fields of a class and of its nested classes,
     * in the format of {@link #entry}, by handle name (like {@code CLibrary$Ioctl.HANDLE})
     */
    private static Map<String, String> downcalls(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        List<Class<?>> classes = new ArrayList<>();
        classes.add(clazz);
        classes.addAll(Arrays.asList(clazz.getDeclaredClasses()));
        Map<String, String> downcalls = new TreeMap<>();
        for (Class<?> c : classes)
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != MethodHandle.class)
                    continue;
                String name = c.getName().substring(c.getPackage().getName().length() + 1) + "." + field.getName();
                field.setAccessible(true);
                MethodHandle handle = (MethodHandle) field.get(null);
                assertNotNull(handle, name + " is null (symbol not found, or initialization error)");
                Map<String, String> options = downcallOptions.getOrDefault(name, Collections.emptyMap());
                List<Class<?>> paramTypes = handle.type().parameterList();
                if (Boolean.parseBoolean(options.get("captureCallState"))) {
                    assertFalse(paramTypes.isEmpty(), name + " should have a call state parameter");
                    assertEquals("void*", nativeType(paramTypes.get(0)), name + " call state parameter");
                    paramTypes = paramTypes.subList(1, paramTypes.size());
                }
                List<String> params = new ArrayList<>();
                for (Class<?> param : paramTypes)
                    params.add(nativeType(param));
                String descriptor = "(" + String.join(", ", params) + ")" + nativeType(handle.type().returnType());
                downcalls.put(name, entry(descriptor, options));
            }
        assertFalse(downcalls.isEmpty(), "no downcall handle found in " + className);
        return downcalls;
    }

    @Test
    void metadataFilesDeclareTheSameDowncalls() throws Exception {
        List<String> reachabilityMetadata = reachabilityMetadataDowncalls();
        List<String> foreignConfig = foreignConfigDowncalls();
        assertFalse(reachabilityMetadata.isEmpty());
        assertEquals(
                new HashSet<>(reachabilityMetadata).size(),
                reachabilityMetadata.size(),
                "duplicated entries in reachability-metadata.json: " + reachabilityMetadata);
        assertEquals(
                new TreeSet<>(reachabilityMetadata),
                new TreeSet<>(foreignConfig),
                "reachability-metadata.json and foreign-config.json should declare the same downcalls");
        assertEquals(reachabilityMetadata.size(), foreignConfig.size());
    }

    /**
     * Checks that the downcalls of the FFM classes of the current OS are all registered
     * (CLibrary on Linux / Mac, Kernel32 on Windows, as Kernel32 can't be loaded elsewhere).
     */
    @Test
    void downcallsAreRegistered() throws Exception {
        assumeTrue(javaVersion() >= 22, "FFM classes only used on Java >= 22");
        String className = isWindows()
                ? "io.github.alexarchambault.nativeterm.internal.Kernel32"
                : "io.github.alexarchambault.nativeterm.internal.CLibrary";
        Set<String> reachabilityMetadata = new HashSet<>(reachabilityMetadataDowncalls());
        Set<String> foreignConfig = new HashSet<>(foreignConfigDowncalls());
        Map<String, String> downcalls = downcalls(className);
        for (Map.Entry<String, String> e : downcalls.entrySet()) {
            String desc = className + "." + e.getKey() + " " + e.getValue();
            assertTrue(reachabilityMetadata.contains(e.getValue()), desc + " not in reachability-metadata.json");
            assertTrue(foreignConfig.contains(e.getValue()), desc + " not in foreign-config.json");
        }
        // Check that options were found for all the handles that have some (catches handle renames)
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        for (String name : downcallOptions.keySet())
            if (name.startsWith(simpleName + ".") || name.startsWith(simpleName + "$"))
                assertTrue(downcalls.containsKey(name), name + " handle not found in " + downcalls.keySet());
    }
}
