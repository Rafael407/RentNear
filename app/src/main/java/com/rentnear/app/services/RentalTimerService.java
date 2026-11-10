package com.rentnear.app.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.rentnear.app.MainActivity;
import com.rentnear.app.model.Booking;
import com.rentnear.app.util.Format;
import com.rentnear.app.util.Pricing;
import com.rentnear.app.workers.ReminderWorker;

/**
 * Foreground service that keeps an ongoing notification in the tray with the time left on a
 * rental (or how late it is, with the late fee so far). The text refreshes every 20 seconds.
 * The renter starts it from the booking screen and can stop it from the notification.
 */
public class RentalTimerService extends Service {
    public static final String CHANNEL_ID = "timer";
    private static final String ACTION_STOP = "com.rentnear.app.STOP_TIMER";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_END = "endMs";
    private static final String EXTRA_RATE = "rate";
    private static final String EXTRA_TOTAL = "total";
    private static final String EXTRA_UNIT = "unit";
    private static final int NOTIFICATION_ID = 4101;
    private static final long TICK_MS = 20_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String bookingId = "";
    private String title = "";
    private long endMs;
    private double rate;
    private double total;
    private String unit = "day";

    public static void start(Context context, Booking b) {
        Intent i = new Intent(context, RentalTimerService.class)
                .putExtra(ReminderWorker.KEY_BOOKING, b.id)
                .putExtra(EXTRA_TITLE, b.itemTitle)
                .putExtra(EXTRA_END, b.endMs)
                .putExtra(EXTRA_RATE, b.rate)
                .putExtra(EXTRA_TOTAL, b.total)
                .putExtra(EXTRA_UNIT, b.unit);
        ContextCompat.startForegroundService(context, i);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RentalTimerService.class));
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        bookingId = intent.getStringExtra(ReminderWorker.KEY_BOOKING);
        title = intent.getStringExtra(EXTRA_TITLE);
        endMs = intent.getLongExtra(EXTRA_END, 0);
        rate = intent.getDoubleExtra(EXTRA_RATE, 0);
        total = intent.getDoubleExtra(EXTRA_TOTAL, 0);
        unit = intent.getStringExtra(EXTRA_UNIT);

        int type = Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(), type);

        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::tick, TICK_MS);
        return START_NOT_STICKY;
    }

    private void tick() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, build());
        } catch (SecurityException ignored) {
            // notification permission was revoked; the service keeps running silently
        }
        handler.postDelayed(this::tick, TICK_MS);
    }

    private Notification build() {
        long now = System.currentTimeMillis();
        long left = endMs - now;
        String heading;
        String text;
        if (left > 0) {
            heading = "Due in " + span(left);
            text = "“" + title + "” is due back soon.";
        } else {
            double fee = Pricing.lateFee(rate, total, unit, endMs, now);
            heading = "Overdue by " + span(-left);
            text = fee > 0 ? "Late fee so far: " + Format.money(fee) + ". Return “" + title + "” now."
                    : "Within the 15 minute grace period for “" + title + "”.";
        }

        Intent open = new Intent(this, MainActivity.class)
                .putExtra(ReminderWorker.KEY_BOOKING, bookingId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, RentalTimerService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(heading)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
                .build();
    }

    private static String span(long ms) {
        long minutes = Math.max(0, ms / 60_000);
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        if (days > 0) return days + " d " + hours + " h";
        if (hours > 0) return hours + " h " + mins + " min";
        return mins + " min";
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
