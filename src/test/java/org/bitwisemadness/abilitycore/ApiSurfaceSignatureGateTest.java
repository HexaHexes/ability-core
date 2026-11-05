package org.bitwisemadness.abilitycore;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Signature surface gate (REQ-CORE-09, core#20 AC2): every consumer-facing
 * signature in a supported package may reference only supported-package types
 * plus what consumers already compile against (Minecraft, NeoForge, java.*).
 * A signature naming an internal-only type compiles green here but is
 * unusable from another repo - the exact failure mode the bounded-surface
 * policy exists to prevent.
 *
 * <p>Sibling of {@link ApiLeakGateTest}: that gate byte-scans for upstream mod
 * types anywhere in the contract packages; this one walks <em>signatures</em>
 * (declared public/protected methods, fields, constructors and supertypes,
 * including generic arguments) for core's own non-supported packages.
 * Internal types in method bodies - the {@code MOD_ID} and {@code PIPELINE}
 * constants - are implementation, not surface, and stay allowed.</p>
 */
class ApiSurfaceSignatureGateTest {

    private static final String CORE_PREFIX = "org.bitwisemadness.abilitycore";

    /** Mirrors docs/api-surface.md's supported-packages table exactly. */
    private static final Set<String> SUPPORTED_PACKAGES =
            Set.of("api", "registry", "discovery", "activation", "sync", "config");

    @Test
    void consumerFacingSignaturesExposeOnlySupportedTypes() throws Exception {
        Path mainClasses = Path.of(AbilityCore.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassLoader loader = ApiSurfaceSignatureGateTest.class.getClassLoader();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainClasses)) {
            for (Path classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                String relative = mainClasses.relativize(classFile).toString().replace('\\', '/');
                String binaryName = supportedBinaryName(relative);
                if (binaryName == null) {
                    continue;
                }
                Class<?> type = Class.forName(binaryName, false, loader);
                inspect(type, violations);
            }
        }
        if (!violations.isEmpty()) {
            fail("Internal-only types leaked into consumer-facing signatures:\n"
                    + String.join("\n", violations)
                    + "\nSupported packages come from docs/api-surface.md - moving one there (or"
                    + " hiding the type) is an API change, reviewed as such.");
        }
    }

    /** The binary name if {@code relative} is in a supported package, else null. */
    private static String supportedBinaryName(String relative) {
        String root = "org/bitwisemadness/abilitycore/";
        if (!relative.startsWith(root) || !relative.endsWith(".class")) {
            return null;
        }
        String afterRoot = relative.substring(root.length());
        int slash = afterRoot.indexOf('/');
        if (slash < 0) {
            return null; // the root package (AbilityCore) is internal by definition
        }
        String pkg = afterRoot.substring(0, slash);
        if (!SUPPORTED_PACKAGES.contains(pkg)) {
            return null; // any future internal package is simply not scanned - and not supported
        }
        return relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
    }

    private static void inspect(Class<?> type, List<String> violations) {
        inspectMethods(type, violations);
        inspectFields(type, violations);
        inspectConstructors(type, violations);
        check(type.getGenericSuperclass(), type.getSimpleName() + " extends", violations);
        for (Type iface : type.getGenericInterfaces()) {
            check(iface, type.getSimpleName() + " implements", violations);
        }
    }

    private static void inspectMethods(Class<?> type, List<String> violations) {
        for (Method method : type.getDeclaredMethods()) {
            if (!consumerVisible(method.getModifiers())) {
                continue;
            }
            String where = type.getSimpleName() + "#" + method.getName();
            check(method.getGenericReturnType(), where + " return", violations);
            for (Type parameter : method.getGenericParameterTypes()) {
                check(parameter, where + " parameter", violations);
            }
            for (Type thrown : method.getGenericExceptionTypes()) {
                check(thrown, where + " throws", violations);
            }
        }
    }

    private static void inspectFields(Class<?> type, List<String> violations) {
        for (Field field : type.getDeclaredFields()) {
            if (consumerVisible(field.getModifiers())) {
                check(field.getGenericType(), type.getSimpleName() + "." + field.getName() + " field", violations);
            }
        }
    }

    private static void inspectConstructors(Class<?> type, List<String> violations) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!consumerVisible(constructor.getModifiers())) {
                continue;
            }
            for (Type parameter : constructor.getGenericParameterTypes()) {
                check(parameter, type.getSimpleName() + " constructor", violations);
            }
        }
    }

    private static boolean consumerVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void check(Type type, String where, List<String> violations) {
        check(type, where, violations, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void check(Type type, String where, List<String> violations, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof Class<?> raw) {
            Class<?> element = raw;
            while (element.isArray()) {
                element = element.getComponentType();
            }
            checkClassName(element.getName(), where, violations);
        } else if (type instanceof ParameterizedType parameterized) {
            check(parameterized.getRawType(), where, violations, visited);
            for (Type argument : parameterized.getActualTypeArguments()) {
                check(argument, where, violations, visited);
            }
        } else if (type instanceof GenericArrayType arrayType) {
            check(arrayType.getGenericComponentType(), where, violations, visited);
        } else {
            checkComposite(type, where, violations, visited);
        }
    }

    private static void checkComposite(Type type, String where, List<String> violations, Set<Type> visited) {
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                check(bound, where, violations, visited);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                check(bound, where, violations, visited);
            }
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                check(bound, where, violations, visited);
            }
        }
    }

    private static void checkClassName(String name, String where, List<String> violations) {
        if (!name.startsWith(CORE_PREFIX)) {
            return; // platform, Minecraft, NeoForge or annotation types - consumers have these
        }
        String remainder = name.substring(CORE_PREFIX.length());
        String pkg = remainder.startsWith(".") ? remainder.substring(1) : remainder;
        int dot = pkg.indexOf('.');
        String firstSegment = dot < 0 ? pkg : pkg.substring(0, dot);
        if (!SUPPORTED_PACKAGES.contains(firstSegment)) {
            violations.add(where + " -> " + name + " (package '" + firstSegment
                    + "' is not in docs/api-surface.md's supported set)");
        }
    }
}
