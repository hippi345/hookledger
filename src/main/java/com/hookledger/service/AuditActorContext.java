package com.hookledger.service;

public final class AuditActorContext {

    public static final String ACTOR_HEADER = "X-Hookledger-Actor";
    public static final String DEFAULT_ACTOR = "system";

    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private AuditActorContext() {}

    public static void setActor(String actor) {
        if (actor == null || actor.isBlank()) {
            ACTOR.remove();
        } else {
            ACTOR.set(actor.trim());
        }
    }

    public static void clear() {
        ACTOR.remove();
    }

    public static String currentActor() {
        String actor = ACTOR.get();
        return actor != null && !actor.isBlank() ? actor : DEFAULT_ACTOR;
    }
}
