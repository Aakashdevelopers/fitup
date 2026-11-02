package com.amstudio.fitbond.helper;

import android.content.Context;

import com.onesignal.Continue;
import com.onesignal.OneSignal;

public class OneSignalVerificationHelper {

    public static void setupPushSubscriptionObserver(Context context) {
        if (context == null) return;
        OneSignal.getNotifications().requestPermission(true, Continue.with(r -> {}));
    }

    public static void showIntegrationCompleteDialog(Context context) {
        // Dialog removed
    }
}
