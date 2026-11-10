package com.rentnear.app.workers;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rentnear.app.MainActivity;
import com.rentnear.app.model.Booking;

import java.util.concurrent.TimeUnit;

/**
 * Shows one local notification: "due in 24 h / 3 h / 30 min / now". Before showing it, the worker
 * re-reads the booking (1 Firestore read) and stays silent if the item has already been returned.
 */
public class ReminderWorker extends Worker {
    public static final String CHANNEL_ID = "reminders";
    public static final String KEY_BOOKING = "bookingId";
    public static final String KEY_TITLE = "title";
    public static final String KEY_MINUTES = "minutes";
    public static final String KEY_RENTER = "isRenter";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    @SuppressLint("MissingPermission")
    public Result doWork() {
        Data d = getInputData();
        String id = d.getString(KEY_BOOKING);
        if (id == null) return Result.success();
        String title = d.getString(KEY_TITLE);
        int minutes = d.getInt(KEY_MINUTES, 0);
        boolean renter = d.getBoolean(KEY_RENTER, true);

        Context c = getApplicationContext();
        if (!com.rentnear.app.util.Prefs.remindersOn()
                || !NotificationManagerCompat.from(c).areNotificationsEnabled()) return Result.success();
        if (!stillInUse(id)) return Result.success();

        String when = minutes >= 1440 ? "in 24 hours"
                : minutes >= 180 ? "in 3 hours"
                : minutes >= 30 ? "in 30 minutes" : "now";
        String heading;
        String text;
        if (minutes == 0) {
            heading = renter ? "Due now" : "Rental due now";
            text = renter
                    ? "“" + title + "” is due now. Late fees start 15 minutes after the due time."
                    : "“" + title + "” is due back now.";
        } else {
            heading = renter ? "Return reminder" : "Rental due soon";
            text = renter
                    ? "“" + title + "” is due back " + when + "."
                    : "“" + title + "” should come back " + when + ".";
        }

        Intent open = new Intent(c, MainActivity.class);
        open.putExtra(KEY_BOOKING, id);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int notificationId = (id + "_" + minutes).hashCode();
        PendingIntent pi = PendingIntent.getActivity(c, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(heading)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManagerCompat.from(c).notify(notificationId, nb.build());
        return Result.success();
    }

    /** If the booking cannot be read (offline), assume it is still active and notify anyway. */
    private boolean stillInUse(String bookingId) {
        try {
            DocumentSnapshot s = Tasks.await(FirebaseFirestore.getInstance()
                    .collection("bookings").document(bookingId).get(), 20, TimeUnit.SECONDS);
            String st = s.getString("status");
            return Booking.HANDED_OVER.equals(st) || Booking.OVERDUE.equals(st);
        } catch (Exception e) {
            return true;
        }
    }
}
