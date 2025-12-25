package com.demo.accessiblenav.tenant;

/**
 * ThreadLocal holder for current tenant ID.
 * Set by TenantFilter on each request; cleared after the request completes.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static final String DEFAULT_TENANT = "default";

    private TenantContext() {}

    public static String get() {
        String tid = CURRENT.get();
        return tid != null ? tid : DEFAULT_TENANT;
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
