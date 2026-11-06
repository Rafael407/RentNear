package com.rentnear.app.util;

import com.rentnear.app.model.Item;

/** Rental price maths. Must match the checks in firestore.rules. */
public final class Pricing {
    /** Items with a replacement value above this need a Trusted renter. Same number as in the rules. */
    public static final double TRUSTED_VALUE_LIMIT = 15000;
    public static final long MAX_DURATION_MS = 30L * 24 * 3600 * 1000;

    private Pricing() {}

    public static long unitMs(String unit) {
        return Item.UNIT_HOUR.equals(unit) ? 3600_000L : 86_400_000L;
    }

    /** Whole units charged: any part of a unit counts as a full one (minimum 1). */
    public static int units(long startMs, long endMs, String unit) {
        long u = unitMs(unit);
        long dur = endMs - startMs;
        if (dur <= 0) return 1;
        return (int) Math.max(1, (dur + u - 1) / u);
    }

    public static double total(double rate, int units) {
        return rate * units;
    }

    /** No late fee for the first 15 minutes after the due time. */
    public static final long GRACE_MS = 15 * 60_000L;

    /**
     * Late fee: half the rate for every started unit after the grace period, capped at the booking
     * total. Same formula as lateFeeAt() in firestore.rules.
     */
    public static double lateFee(double rate, double total, String unit, long endMs, long nowMs) {
        long late = nowMs - endMs - GRACE_MS;
        if (late <= 0) return 0;
        long um = unitMs(unit);
        long units = (late + um - 1) / um;
        double fee = units * rate * 0.5;
        return Math.min(fee, total);
    }
}
