package com.risk.gateway.interceptor;

/**
 * ThreadLocal context for storing request snapshots during async data collection.
 */
public class RequestContext {

    private static final ThreadLocal<RequestSnapshot> CONTEXT = new ThreadLocal<>();

    public static void setSnapshot(RequestSnapshot snapshot) {
        CONTEXT.set(snapshot);
    }

    public static RequestSnapshot getSnapshot() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
