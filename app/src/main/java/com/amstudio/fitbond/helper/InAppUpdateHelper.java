package com.amstudio.fitbond.helper;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.gms.tasks.Task;

public class InAppUpdateHelper {

    private static final String TAG = "InAppUpdateHelper";
    public static final int REQUEST_CODE_APP_UPDATE = 8888;

    private final AppCompatActivity activity;
    private final AppUpdateManager appUpdateManager;
    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher;
    private InstallStateUpdatedListener installStateUpdatedListener;
    @AppUpdateType
    private int preferredUpdateType = AppUpdateType.FLEXIBLE;

    public InAppUpdateHelper(@NonNull AppCompatActivity activity) {
        this.activity = activity;
        this.appUpdateManager = AppUpdateManagerFactory.create(activity.getApplicationContext());
        this.updateLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        Log.w(TAG, "Update flow failed or canceled by user. Result code: " + result.getResultCode());
                        if (preferredUpdateType == AppUpdateType.IMMEDIATE) {
                            checkForAppUpdate();
                        }
                    }
                }
        );
    }

    /**
     * Set preferred update type: AppUpdateType.FLEXIBLE or AppUpdateType.IMMEDIATE
     */
    public InAppUpdateHelper setUpdateType(@AppUpdateType int updateType) {
        this.preferredUpdateType = updateType;
        return this;
    }

    /**
     * Checks for Play Store app updates and initiates update flow if an update is available.
     */
    public void checkForAppUpdate() {
        if (preferredUpdateType == AppUpdateType.FLEXIBLE) {
            setupFlexibleUpdateListener();
        }

        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (preferredUpdateType == AppUpdateType.IMMEDIATE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    startUpdateFlow(appUpdateInfo, AppUpdateType.IMMEDIATE);
                } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    startUpdateFlow(appUpdateInfo, AppUpdateType.FLEXIBLE);
                } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    // Fallback to Immediate if Flexible is not allowed
                    startUpdateFlow(appUpdateInfo, AppUpdateType.IMMEDIATE);
                }
            } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to check for Play Store app update", e);
        });
    }

    private void startUpdateFlow(AppUpdateInfo appUpdateInfo, @AppUpdateType int updateType) {
        try {
            AppUpdateOptions options = AppUpdateOptions.newBuilder(updateType).build();
            appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    options
            );
        } catch (Exception e) {
            Log.e(TAG, "Error starting update flow", e);
        }
    }

    private void setupFlexibleUpdateListener() {
        if (installStateUpdatedListener == null) {
            installStateUpdatedListener = state -> {
                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                    popupSnackbarForCompleteUpdate();
                } else if (state.installStatus() == InstallStatus.FAILED) {
                    Log.e(TAG, "Flexible update installation failed with errorCode: " + state.installErrorCode());
                }
            };
            appUpdateManager.registerListener(installStateUpdatedListener);
        }
    }

    /**
     * Call this in Activity's onResume() to handle pending updates.
     */
    public void onResume() {
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (preferredUpdateType == AppUpdateType.IMMEDIATE) {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // Resume immediate update flow if user left and returned to app
                    startUpdateFlow(appUpdateInfo, AppUpdateType.IMMEDIATE);
                }
            } else {
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    popupSnackbarForCompleteUpdate();
                }
            }
        });
    }

    /**
     * Shows a Snackbar notifying the user that the update has downloaded and asks them to restart.
     */
    private void popupSnackbarForCompleteUpdate() {
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            rootView = activity.getWindow().getDecorView().getRootView();
        }
        if (rootView == null) return;

        Snackbar snackbar = Snackbar.make(
                rootView,
                "An update has just been downloaded.",
                Snackbar.LENGTH_INDEFINITE
        );
        snackbar.setAction("RESTART", v -> appUpdateManager.completeUpdate());
        snackbar.show();
    }

    /**
     * Call in Activity's onActivityResult to handle result if needed.
     */
    public void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == REQUEST_CODE_APP_UPDATE) {
            if (resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "Update flow failed or canceled by user. Result code: " + resultCode);
                if (preferredUpdateType == AppUpdateType.IMMEDIATE) {
                    // If immediate update was canceled, re-prompt or check update
                    checkForAppUpdate();
                }
            }
        }
    }

    /**
     * Call this in Activity's onDestroy() to unregister listeners.
     */
    public void onDestroy() {
        if (installStateUpdatedListener != null) {
            appUpdateManager.unregisterListener(installStateUpdatedListener);
            installStateUpdatedListener = null;
        }
    }
}
