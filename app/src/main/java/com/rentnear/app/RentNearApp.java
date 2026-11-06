package com.rentnear.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.rentnear.app.workers.ReminderWorker;

import org.maplibre.android.MapLibre;

/** Application class: initialises MapLibre and the reminder notification channel. */
public class RentNearApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MapLibre.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(ReminderWorker.CHANNEL_ID,
                    "Rental reminders", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Reminders before a rented item is due back");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
