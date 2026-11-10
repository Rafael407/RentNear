package com.rentnear.app.data.cache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rentnear.app.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the last nearby search in Room. A saved search is reused only if it is recent and was made
 * close to where the user is now, so the feed never shows listings from somewhere else.
 */
public final class ListingCache {
    public interface Callback {
        /** items is null when nothing usable is saved. Always called on the main thread. */
        void onLoaded(List<Item> items);
    }

    private static final long MAX_AGE_MS = 24L * 3600 * 1000;
    private static final double MAX_MOVE_M = 1500;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Gson GSON = new Gson();

    private ListingCache() {}

    public static void save(Context context, GeoLocation at, double radiusM, List<Item> items) {
        Context app = context.getApplicationContext();
        List<Item> copy = new ArrayList<>(items);
        IO.execute(() -> {
            try {
                CachedSearch row = new CachedSearch();
                row.json = GSON.toJson(copy);
                row.lat = at.latitude;
                row.lng = at.longitude;
                row.radiusM = radiusM;
                row.savedAtMs = System.currentTimeMillis();
                AppDb.get(app).cacheDao().save(row);
            } catch (Exception ignored) {
                // the cache is a convenience; never fail a search because of it
            }
        });
    }

    public static void load(Context context, GeoLocation at, double radiusM, Callback cb) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<Item> result = null;
            try {
                CachedSearch row = AppDb.get(app).cacheDao().load();
                if (row != null && row.json != null
                        && System.currentTimeMillis() - row.savedAtMs <= MAX_AGE_MS
                        && row.radiusM == radiusM
                        && GeoFireUtils.getDistanceBetween(new GeoLocation(row.lat, row.lng), at) <= MAX_MOVE_M) {
                    result = GSON.fromJson(row.json, new TypeToken<List<Item>>() {}.getType());
                }
            } catch (Exception ignored) {
                // treat a broken cache as empty
            }
            List<Item> out = result;
            MAIN.post(() -> cb.onLoaded(out));
        });
    }

    public static void clear(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                AppDb.get(app).cacheDao().clear();
            } catch (Exception ignored) {
                // nothing to clear
            }
            MAIN.post(done);
        });
    }
}
