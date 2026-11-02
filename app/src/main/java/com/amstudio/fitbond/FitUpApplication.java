package com.amstudio.fitbond;

import android.app.Application;

import com.amstudio.fitbond.helper.OneSignalManager;
import com.amstudio.fitbond.repository.TencentChatManager;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class FitUpApplication extends Application {

    private static FitUpApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize OneSignal Push Notification SDK off the main thread
        OneSignalManager.getInstance().initialize(this, OneSignalManager.DEFAULT_APP_ID);

        // If user is logged in, link user ID with OneSignal
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            OneSignalManager.getInstance().login(currentUser.getUid());
        }

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
