package com.rentnear.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.Nullable;

/**
 * Listens for system broadcasts: airplane mode, low battery, and battery saver. The Home screen
 * shows a banner while one of them is active. These broadcasts cannot be declared in the manifest
 * on modern Android, so MainActivity registers this receiver while it is on screen.
 */
public class SystemEventReceiver extends BroadcastReceiver {
    public interface Listener {
        /** banner is null when everything is normal; toast is non-null only for a change just now. */
        void onStatus(@Nullable String banner, @Nullable String toast);
    }

    private final Listener listener;
    private boolean airplane;
    private boolean batteryLow;
    private boolean powerSave;

    public SystemEventReceiver(Context context, Listener listener) {
        this.listener = listener;
        airplane = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        powerSave = pm != null && pm.isPowerSaveMode();
    }

    public static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_LOW);
        f.addAction(Intent.ACTION_BATTERY_OKAY);
        f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        return f;
    }

    /** Reports the current state once, right after registering. */
    public void reportNow() {
        listener.onStatus(banner(), null);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        String toast = null;
        switch (action) {
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                airplane = intent.getBooleanExtra("state", false);
                toast = airplane ? "Airplane mode on: you are offline." : "Airplane mode off: back online.";
                break;
            case Intent.ACTION_BATTERY_LOW:
                batteryLow = true;
                toast = "Battery is low.";
                break;
            case Intent.ACTION_BATTERY_OKAY:
                batteryLow = false;
                toast = "Battery is OK again.";
                break;
            case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                powerSave = pm != null && pm.isPowerSaveMode();
                toast = powerSave ? "Battery saver on." : "Battery saver off.";
                break;
            default:
                return;
        }
        listener.onStatus(banner(), toast);
    }

    @Nullable
    private String banner() {
        if (airplane) return "Airplane mode is on. You are offline, so bookings, chat and photos will not sync until you reconnect.";
        if (batteryLow) return "Battery is low. Finish handover or return photos soon, and keep the app open for the QR scan.";
        if (powerSave) return "Battery saver is on. Return reminders may arrive late.";
        return null;
    }
}
