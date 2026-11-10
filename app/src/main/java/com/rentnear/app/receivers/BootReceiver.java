package com.rentnear.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.rentnear.app.workers.ResyncRemindersWorker;

/** Declared in the manifest: after a reboot or an app update, rebuild the return and debt reminders. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork("resync_reminders",
                    ExistingWorkPolicy.REPLACE, new OneTimeWorkRequest.Builder(ResyncRemindersWorker.class).build());
        }
    }
}
