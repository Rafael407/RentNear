package com.rentnear.app.util;

import com.rentnear.app.model.Item;

import java.util.Locale;

public final class Format {
    public static final String CURRENCY = "৳";

    private Format() {}

    public static String price(Item i) {
        return CURRENCY + trim(i.price) + " / " + (Item.UNIT_DAY.equals(i.priceUnit) ? "day" : "hour");
    }

    public static String money(double v) {
        return CURRENCY + trim(v);
    }

    public static String distance(double metres) {
        if (metres < 1000) return Math.round(metres) + " m";
        return String.format(Locale.US, "%.1f km", metres / 1000.0);
    }

    public static String duration(double seconds) {
        long min = Math.max(1, Math.round(seconds / 60.0));
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }

    private static String trim(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }
}
