package com.rentnear.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/** The few settings the user can change. Call {@link #init} once from the Application class. */
public final class Prefs {
    public static final int[] RADIUS_KM = {5, 10, 20};
    public static final int DEFAULT_RADIUS_KM = 10;

    private static final String FILE = "rentnear_prefs";
    private static final String KEY_RADIUS = "radius_km";
    private static final String KEY_REMINDERS = "reminders_on";

    private static SharedPreferences prefs;

    private Prefs() {}

    public static void init(Context app) {
        prefs = app.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int radiusKm() {
        return prefs == null ? DEFAULT_RADIUS_KM : prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS_KM);
    }

    public static void setRadiusKm(int km) {
        if (prefs != null) prefs.edit().putInt(KEY_RADIUS, km).apply();
    }

    public static double radiusMetres() {
        return radiusKm() * 1000.0;
    }

    public static boolean remindersOn() {
        return prefs == null || prefs.getBoolean(KEY_REMINDERS, true);
    }

    public static void setRemindersOn(boolean on) {
        if (prefs != null) prefs.edit().putBoolean(KEY_REMINDERS, on).apply();
    }
}
