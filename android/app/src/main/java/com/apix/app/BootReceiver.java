package com.apix.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        // No-op: we no longer run a foreground service for notifications.
        // Realtime listener starts inside ApixApplication.onCreate() when the app launches.
    }
}