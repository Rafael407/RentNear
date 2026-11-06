package com.rentnear.app.util;

/** Friendly level names for points. */
public final class Tiers {
    private Tiers() {}

    public static String forPoints(long points) {
        if (points >= 1000) return "Platinum";
        if (points >= 500) return "Gold";
        if (points >= 150) return "Silver";
        return "Bronze";
    }

    public static String stars(int rating) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) sb.append(i <= rating ? '★' : '☆');
        return sb.toString();
    }
}
