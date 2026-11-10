package com.rentnear.app.workers;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.rentnear.app.model.Booking;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the due-time reminders (24 h, 3 h, 30 min before, and at the due time) for bookings that
 * are in use. Safe to call repeatedly: each reminder has a unique name and is kept if it exists.
 * Called whenever the app sees fresh booking data (Rentals tab, booking detail).
 */
public final class ReminderScheduler {
    /** Minutes before the due time. 0 = at the due time. */
    private static final int[] OFFSETS_MIN = {1440, 180, 30, 0};

    private ReminderScheduler() {}

    public static void sync(Context context, @Nullable List<Booking> bookings, @Nullable String me) {
        if (bookings == null || me == null || !com.rentnear.app.util.Prefs.remindersOn()) return;
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        long now = System.currentTimeMillis();

        for (Booking b : bookings) {
            boolean active = Booking.HANDED_OVER.equals(b.status) || Booking.OVERDUE.equals(b.status);
            boolean finished = Booking.RETURNED.equals(b.status) || Booking.COMPLETED.equals(b.status)
                    || Booking.CANCELLED.equals(b.status) || Booking.DECLINED.equals(b.status)
                    || Booking.DISPUTED.equals(b.status);
            if (!active && !finished) continue;

            for (int minutes : OFFSETS_MIN) {
                String name = "rem_" + b.id + "_" + minutes;
                if (finished) {
                    wm.cancelUniqueWork(name);
                    continue;
                }
                long delay = b.endMs - minutes * 60_000L - now;
                if (delay <= 0) continue;

                Data data = new Data.Builder()
                        .putString(ReminderWorker.KEY_BOOKING, b.id)
                        .putString(ReminderWorker.KEY_TITLE, b.itemTitle == null ? "Your rental" : b.itemTitle)
                        .putInt(ReminderWorker.KEY_MINUTES, minutes)
                        .putBoolean(ReminderWorker.KEY_RENTER, me.equals(b.renterId))
                        .build();
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP,
                        new OneTimeWorkRequest.Builder(ReminderWorker.class)
                                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                                .setInputData(data)
                                .build());
            }
        }
    }
}
