package com.rentnear.app.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rentnear.app.model.Booking;
import com.rentnear.app.model.Debt;
import com.rentnear.app.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Reads my bookings and debts once and schedules their reminders again (run after a reboot). */
public class ResyncRemindersWorker extends Worker {
    public ResyncRemindersWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || !Prefs.remindersOn()) return Result.success();
        Context c = getApplicationContext();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        try {
            List<Booking> bookings = new ArrayList<>();
            for (String field : new String[]{"renterId", "ownerId"}) {
                for (DocumentSnapshot d : Tasks.await(db.collection("bookings").whereEqualTo(field, uid)
                        .limit(100).get(), 30, TimeUnit.SECONDS).getDocuments()) {
                    bookings.add(Booking.from(d));
                }
            }
            ReminderScheduler.sync(c, bookings, uid);

            List<Debt> debts = new ArrayList<>();
            for (DocumentSnapshot d : Tasks.await(db.collection("debts").whereEqualTo("debtorId", uid)
                    .limit(100).get(), 30, TimeUnit.SECONDS).getDocuments()) {
                debts.add(Debt.from(d));
            }
            DebtReminderScheduler.sync(c, debts, uid);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
