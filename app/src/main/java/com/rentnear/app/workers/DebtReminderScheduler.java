package com.rentnear.app.workers;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.rentnear.app.model.Debt;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Local reminders 7, 3 and 1 days before a debt's deadline. Safe to call repeatedly. */
public final class DebtReminderScheduler {
    private static final int[] DAYS_BEFORE = {7, 3, 1};

    private DebtReminderScheduler() {}

    public static void sync(Context context, @Nullable List<Debt> debts, @Nullable String me) {
        if (debts == null || me == null || !com.rentnear.app.util.Prefs.remindersOn()) return;
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        long now = System.currentTimeMillis();
        for (Debt d : debts) {
            if (!me.equals(d.debtorId)) continue;
            for (int days : DAYS_BEFORE) {
                String name = "debt_" + d.id + "_" + days;
                if (d.paid) {
                    wm.cancelUniqueWork(name);
                    continue;
                }
                long delay = d.dueMs - days * 86_400_000L - now;
                if (delay <= 0) continue;
                Data data = new Data.Builder()
                        .putString(DebtReminderWorker.KEY_DEBT, d.id)
                        .putString(DebtReminderWorker.KEY_TITLE, d.itemTitle == null ? "" : d.itemTitle)
                        .putInt(DebtReminderWorker.KEY_DAYS, days)
                        .build();
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP,
                        new OneTimeWorkRequest.Builder(DebtReminderWorker.class)
                                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                                .setInputData(data)
                                .build());
            }
        }
    }
}
