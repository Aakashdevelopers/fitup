package com.amstudio.fitup;

import android.app.Application;

import com.amstudio.fitup.repository.TencentChatManager;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class FitUpApplication extends Application {

    private static FitUpApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Register application context for Tencent Chat SDK initialization
        TencentChatManager.ContextHolder.setContext(this);

        // Initialize Firebase App Check
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
        }
    }

    public static FitUpApplication getInstance() {
        return instance;
    }
}
