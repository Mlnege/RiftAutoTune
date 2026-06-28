package com.nightfall.riftautotune.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Defensive reflection helpers. Every call returns a sentinel / {@code false} instead of
 * throwing, so adapters that talk to optional mods can never crash the client.
 *
 * <p>NOTE: every reflective access to an external mod is, by definition, a place where the real
 * API/signature must be confirmed against the installed version. Those sites are marked with
 * {@code // TODO: confirm signature} in the adapters.</p>
 */
public final class Reflect {

    private Reflect() {}

    public static Class<?> clazz(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Method method(Class<?> owner, String name, Class<?>... params) {
        if (owner == null) return null;
        try {
            Method m = owner.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object invokeStatic(Method m, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(null, args);
        } catch (Throwable t) {
            RiftLog.debug("static invoke failed: {}", t.toString());
            return null;
        }
    }

    public static Object invoke(Method m, Object target, Object... args) {
        if (m == null || target == null) return null;
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            RiftLog.debug("invoke failed: {}", t.toString());
            return null;
        }
    }

    public static Object getStaticField(Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            Field f = owner.getField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** First public method with the given name and argument count (handles generic bridge methods). */
    public static Method methodByName(Class<?> owner, String name, int argCount) {
        if (owner == null) return null;
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount) {
                try { m.setAccessible(true); } catch (Throwable ignored) {}
                return m;
            }
        }
        return null;
    }

    /** Get an enum constant by name from an enum class given by fully-qualified name, or null. */
    public static Object enumConst(String enumFqcn, String constant) {
        Class<?> c = clazz(enumFqcn);
        if (c == null) return null;
        return getStaticField(c, constant);
    }
}
