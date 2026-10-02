package com.hookledger.domain;

public final class EventStateFlags {

    public static final int SIGNED = 1;
    public static final int REPLAYED = 2;
    public static final int DUPLICATE = 4;

    private EventStateFlags() {}

    public static boolean isSigned(int flags) {
        return (flags & SIGNED) != 0;
    }

    public static boolean isReplayed(int flags) {
        return (flags & REPLAYED) != 0;
    }

    public static boolean isDuplicate(int flags) {
        return (flags & DUPLICATE) != 0;
    }
}
