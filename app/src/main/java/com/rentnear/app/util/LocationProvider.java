package com.rentnear.app.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/** One-shot current location with a fallback so the app still works without permission. */
public final class LocationProvider {
    /** Fallback centre (Khulna) used when location permission or GPS is unavailable. */
    public static final GeoLocation DEFAULT = new GeoLocation(22.8456, 89.5403);

    public interface Callback {
        /** @param real true if this is the device's actual position, false for the fallback. */
        void onLocation(GeoLocation location, boolean real);
    }

    private LocationProvider() {}

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public static void current(Context context, Callback cb) {
        if (!hasPermission(context)) {
            cb.onLocation(DEFAULT, false);
            return;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        cb.onLocation(new GeoLocation(loc.getLatitude(), loc.getLongitude()), true);
                    } else {
                        client.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (last != null) {
                                        cb.onLocation(new GeoLocation(last.getLatitude(),
                                                last.getLongitude()), true);
                                    } else {
                                        cb.onLocation(DEFAULT, false);
                                    }
                                })
                                .addOnFailureListener(e -> cb.onLocation(DEFAULT, false));
                    }
                })
                .addOnFailureListener(e -> cb.onLocation(DEFAULT, false));
    }
}
