package com.amstudio.fitbond.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.amstudio.fitbond.helper.NotificationHelper;
import com.amstudio.fitbond.repository.ActiveCallManager;

public class CallForegroundService extends Service {

    private static final String TAG = "CallForegroundService";

    public static final String ACTION_START_CALL_SERVICE = "com.amstudio.fitbond.ACTION_START_CALL_SERVICE";
    public static final String ACTION_UPDATE_CALL_SERVICE = "com.amstudio.fitbond.ACTION_UPDATE_CALL_SERVICE";
    public static final String ACTION_STOP_CALL_SERVICE = "com.amstudio.fitbond.ACTION_STOP_CALL_SERVICE";

    public static final String EXTRA_CALL_ID = "extra_call_id";
    public static final String EXTRA_TARGET_UID = "extra_target_uid";
    public static final String EXTRA_USER_NAME = "extra_user_name";
    public static final String EXTRA_AVATAR_URL = "extra_avatar_url";
    public static final String EXTRA_IS_VIDEO = "extra_is_video";
    public static final String EXTRA_IS_MUTED = "extra_is_muted";
    public static final String EXTRA_IS_SPEAKER = "extra_is_speaker";

    private ActiveCallManager.OnDetailedCallStateChangeListener stateChangeListener;

    public static void startService(Context context, String callId, String targetUid, String userName,
                                    String avatarUrl, boolean isVideo, boolean isMuted, boolean isSpeaker) {
        if (context == null) return;
        Intent intent = new Intent(context, CallForegroundService.class);
        intent.setAction(ACTION_START_CALL_SERVICE);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_TARGET_UID, targetUid);
        intent.putExtra(EXTRA_USER_NAME, userName);
        intent.putExtra(EXTRA_AVATAR_URL, avatarUrl);
        intent.putExtra(EXTRA_IS_VIDEO, isVideo);
        intent.putExtra(EXTRA_IS_MUTED, isMuted);
        intent.putExtra(EXTRA_IS_SPEAKER, isSpeaker);

        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start CallForegroundService: " + e.getMessage(), e);
        }
    }

    public static void updateService(Context context, String callId, String targetUid, String userName,
                                     String avatarUrl, boolean isVideo, boolean isMuted, boolean isSpeaker) {
        if (context == null) return;
        Intent intent = new Intent(context, CallForegroundService.class);
        intent.setAction(ACTION_UPDATE_CALL_SERVICE);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_TARGET_UID, targetUid);
        intent.putExtra(EXTRA_USER_NAME, userName);
        intent.putExtra(EXTRA_AVATAR_URL, avatarUrl);
        intent.putExtra(EXTRA_IS_VIDEO, isVideo);
        intent.putExtra(EXTRA_IS_MUTED, isMuted);
        intent.putExtra(EXTRA_IS_SPEAKER, isSpeaker);

        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update CallForegroundService: " + e.getMessage(), e);
        }
    }

    public static void stopService(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, CallForegroundService.class);
        intent.setAction(ACTION_STOP_CALL_SERVICE);
        try {
            context.startService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop CallForegroundService: " + e.getMessage(), e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CallForegroundService onCreate");

        stateChangeListener = (state, callId, targetUid, userName, isVideo) -> {
            if (state == ActiveCallManager.CallState.ENDED ||
                state == ActiveCallManager.CallState.CALL_ENDED ||
                state == ActiveCallManager.CallState.REJECTED ||
                state == ActiveCallManager.CallState.CALL_REJECTED ||
                state == ActiveCallManager.CallState.CANCELLED ||
                state == ActiveCallManager.CallState.CALL_CANCELLED ||
                state == ActiveCallManager.CallState.IDLE) {
                Log.d(TAG, "Call ended state observed in foreground service. Stopping service.");
                stopForegroundAndSelf();
            } else {
                ActiveCallManager manager = ActiveCallManager.getInstance();
                updateNotification(
                        manager.getActiveCallId(),
                        manager.getTargetUid(),
                        manager.getUserName(),
                        manager.getAvatarUrl(),
                        manager.isVideo(),
                        manager.isMuteOn(),
                        manager.isSpeakerOn()
                );
            }
        };

        ActiveCallManager.getInstance().addDetailedListener(stateChangeListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "CallForegroundService onStartCommand action: " + action);

            if (ACTION_STOP_CALL_SERVICE.equals(action)) {
                stopForegroundAndSelf();
                return START_NOT_STICKY;
            }

            String intentCallId = intent.getStringExtra(EXTRA_CALL_ID);
            String targetUid = intent.getStringExtra(EXTRA_TARGET_UID);
            String userName = intent.getStringExtra(EXTRA_USER_NAME);
            String avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL);
            boolean isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false);
            boolean isMuted = intent.getBooleanExtra(EXTRA_IS_MUTED, false);
            boolean isSpeaker = intent.getBooleanExtra(EXTRA_IS_SPEAKER, isVideo);

            String finalCallId = (intentCallId != null && !intentCallId.isEmpty())
                    ? intentCallId : ActiveCallManager.getInstance().getActiveCallId();
            if (targetUid == null) targetUid = ActiveCallManager.getInstance().getTargetUid();
            if (userName == null) userName = ActiveCallManager.getInstance().getUserName();
            if (avatarUrl == null) avatarUrl = ActiveCallManager.getInstance().getAvatarUrl();

            startForegroundNotification(finalCallId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeaker);
        }

        if (!ActiveCallManager.getInstance().isActive()) {
            Log.d(TAG, "CallForegroundService started or restarted with no active call session. Stopping service.");
            stopForegroundAndSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved triggered in CallForegroundService");
        if (ActiveCallManager.getInstance().isActive()) {
            ActiveCallManager manager = ActiveCallManager.getInstance();
            updateNotification(
                    manager.getActiveCallId(),
                    manager.getTargetUid(),
                    manager.getUserName(),
                    manager.getAvatarUrl(),
                    manager.isVideo(),
                    manager.isMuteOn(),
                    manager.isSpeakerOn()
            );
        } else {
            stopForegroundAndSelf();
        }
    }

    private void startForegroundNotification(String rawCallId, String targetUid, String userName,
                                             String avatarUrl, boolean isVideo, boolean isMuted, boolean isSpeaker) {
        String callId = (rawCallId != null && !rawCallId.isEmpty()) ? rawCallId : ActiveCallManager.getInstance().getActiveCallId();
        if (callId == null || callId.isEmpty()) callId = "call_" + System.currentTimeMillis();

        int notificationId = NotificationHelper.getNotificationIdForCall(callId);
        Notification notification = NotificationHelper.buildActiveCallNotification(
                this, callId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeaker
        );

        if (notification != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
                    }
                    if (isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                    }
                    startForeground(notificationId, notification, serviceType);
                } else {
                    startForeground(notificationId, notification);
                }
                Log.d(TAG, "CallForegroundService started foreground notification with ID: " + notificationId);
            } catch (Exception e) {
                Log.e(TAG, "Error starting foreground notification: " + e.getMessage(), e);
                NotificationHelper.showActiveCallNotification(this, callId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeaker);
            }
        }
    }

    private void updateNotification(String callId, String targetUid, String userName,
                                     String avatarUrl, boolean isVideo, boolean isMuted, boolean isSpeaker) {
        if (!ActiveCallManager.getInstance().isActive()) {
            stopForegroundAndSelf();
            return;
        }

        String finalCallId = (callId != null && !callId.isEmpty()) ? callId : ActiveCallManager.getInstance().getActiveCallId();
        int notificationId = NotificationHelper.getNotificationIdForCall(finalCallId);
        Notification notification = NotificationHelper.buildActiveCallNotification(
                this, finalCallId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeaker
        );

        if (notification != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
                    }
                    if (isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                    }
                    startForeground(notificationId, notification, serviceType);
                } else {
                    NotificationHelper.showActiveCallNotification(this, finalCallId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeaker);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating foreground notification: " + e.getMessage(), e);
                NotificationHelper.showActiveCallNotification(this, finalCallId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeaker);
            }
        }
    }

    private void stopForegroundAndSelf() {
        Log.d(TAG, "stopForegroundAndSelf triggered");
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception e) {
            Log.w(TAG, "Error stopping foreground: " + e.getMessage());
        }
        NotificationHelper.cancelActiveCallNotification(this);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (stateChangeListener != null) {
            ActiveCallManager.getInstance().removeDetailedListener(stateChangeListener);
            stateChangeListener = null;
        }
        NotificationHelper.cancelActiveCallNotification(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
