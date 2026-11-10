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

import java.util.concurrent.TimeUnit;

/** Shows "your debt is due in N days" unless the debt has been paid in the meantime. */
public class DebtReminderWorker extends Worker {
    public static final String KEY_DEBT = "debtId";
    public static final String KEY_TITLE = "title";
    public static final String KEY_DAYS = "days";
    /** Extra on the MainActivity intent: open the Debts screen. */
    public static final String EXTRA_OPEN_DEBTS = "openDebts";

    public DebtReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    @SuppressLint("MissingPermission")
    public Result doWork() {
        Data d = getInputData();
        String id = d.getString(KEY_DEBT);
        if (id == null) return Result.success();
        int days = d.getInt(KEY_DAYS, 1);
        String title = d.getString(KEY_TITLE);

        Context c = getApplicationContext();
        if (!com.rentnear.app.util.Prefs.remindersOn()
                || !NotificationManagerCompat.from(c).areNotificationsEnabled()) return Result.success();
        if (!stillOpen(id)) return Result.success();

        String text = "Your debt for “" + title + "” is due in " + days + (days == 1 ? " day" : " days")
                + ". After the deadline your account is banned.";
        Intent open = new Intent(c, MainActivity.class);
        open.putExtra(EXTRA_OPEN_DEBTS, true);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int notificationId = ("debt_" + id + "_" + days).hashCode();
        PendingIntent pi = PendingIntent.getActivity(c, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(c, ReminderWorker.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Debt due soon")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManagerCompat.from(c).notify(notificationId, nb.build());
        return Result.success();
    }

    /** If the debt cannot be read (offline), remind anyway. */
    private boolean stillOpen(String debtId) {
        try {
            DocumentSnapshot s = Tasks.await(FirebaseFirestore.getInstance()
                    .collection("debts").document(debtId).get(), 20, TimeUnit.SECONDS);
            return "open".equals(s.getString("status"));
        } catch (Exception e) {
            return true;
        }
    }
}
