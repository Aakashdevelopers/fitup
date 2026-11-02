package com.amstudio.fitbond.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.amstudio.fitbond.TRTCCallActivity;
import com.amstudio.fitbond.helper.NotificationHelper;
import com.amstudio.fitbond.repository.ActiveCallManager;
import com.amstudio.fitbond.repository.TencentChatManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Receiver to handle Accept, Decline, Mute, Speaker, and End Call actions from Call Notifications.
 */
public class CallActionReceiver extends BroadcastReceiver {

    private static final String TAG = "CallActionReceiver";

    public static final String ACTION_ACCEPT_CALL = "com.amstudio.fitbond.ACTION_ACCEPT_CALL";
    public static final String ACTION_DECLINE_CALL = "com.amstudio.fitbond.ACTION_DECLINE_CALL";
    public static final String ACTION_TOGGLE_MUTE = "com.amstudio.fitbond.ACTION_TOGGLE_MUTE";
    public static final String ACTION_TOGGLE_SPEAKER = "com.amstudio.fitbond.ACTION_TOGGLE_SPEAKER";
    public static final String ACTION_END_CALL = "com.amstudio.fitbond.ACTION_END_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        int notificationId = intent.getIntExtra("notification_id", -1);
        String callId = intent.getStringExtra("extra_call_id");
        String callerUid = intent.getStringExtra("extra_target_uid");
        String callerName = intent.getStringExtra("extra_user_name");
        String callerAvatar = intent.getStringExtra("extra_avatar_url");
        boolean isVideo = intent.getBooleanExtra("extra_is_video", false);

        Log.d(TAG, "CallActionReceiver action received: " + action + " for callId: " + callId);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (ACTION_ACCEPT_CALL.equals(action)) {
            Log.d(TAG, "Call ACCEPT action received from notification");
            if (manager != null && notificationId != -1) {
                manager.cancel(notificationId);
            }

            if (callId == null || callId.isEmpty()) {
                Toast.makeText(context, "Invalid Call Session", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(context, "Please sign in to answer call", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseFirestore.getInstance().collection("calls").document(callId).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String status = doc.getString("status");
                            if ("CALLING".equalsIgnoreCase(status)) {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("status", "ACCEPTED");
                                updates.put("acceptedAt", FieldValue.serverTimestamp());
                                FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates);

                                if (callerUid != null && !callerUid.isEmpty()) {
                                    TencentChatManager.getInstance().sendTrtcCallSignalMessage(
                                            callerUid, callId, isVideo ? "VIDEO" : "VOICE", "ACCEPT", null
                                    );
                                }

                                String displayName = callerName != null ? callerName : "Gym Buddy";
                                String initials = displayName.length() >= 2 ? displayName.substring(0, 2).toUpperCase() : "GB";

                                ActiveCallManager.getInstance().setActiveCall(
                                        callId, callerUid, displayName, initials, callerAvatar, isVideo, System.currentTimeMillis(), ActiveCallManager.CallState.CONNECTING
                                );

                                Intent intentCall = new Intent(context, TRTCCallActivity.class);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_CALL_ID, callId);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_TARGET_UID, callerUid);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_USER_NAME, displayName);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_USER_INITIALS, initials);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_AVATAR_URL, callerAvatar);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_IS_VIDEO, isVideo);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_IS_INCOMING, false);
                                intentCall.putExtra(TRTCCallActivity.EXTRA_AUTO_ACCEPT, true);
                                intentCall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                context.startActivity(intentCall);
                            } else {
                                Toast.makeText(context, "Call no longer available (" + (status != null ? status.toLowerCase() : "ended") + ")", Toast.LENGTH_SHORT).show();
                                NotificationHelper.cancelCallNotification(context, callId);
                            }
                        } else {
                            Toast.makeText(context, "Call has expired or ended", Toast.LENGTH_SHORT).show();
                            NotificationHelper.cancelCallNotification(context, callId);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "Error accepting call: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    });

        } else if (ACTION_DECLINE_CALL.equals(action)) {
            Log.d(TAG, "Call DECLINED from notification");
            if (manager != null && notificationId != -1) {
                manager.cancel(notificationId);
            }
            NotificationHelper.cancelCallNotification(context, callId);

            if (callId != null && !callId.isEmpty()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "REJECTED");
                updates.put("endedAt", FieldValue.serverTimestamp());
                FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates);

                if (callerUid != null && !callerUid.isEmpty()) {
                    TencentChatManager.getInstance().sendTrtcCallSignalMessage(
                            callerUid, callId, isVideo ? "VIDEO" : "VOICE", "REJECT", null
                    );
                }
            }

            ActiveCallManager.getInstance().setCallState(ActiveCallManager.CallState.CALL_REJECTED);
            ActiveCallManager.getInstance().clearActiveCall();

        } else if (ACTION_TOGGLE_MUTE.equals(action)) {
            Log.d(TAG, "Mute toggled from notification action");
            ActiveCallManager.getInstance().toggleMute();

        } else if (ACTION_TOGGLE_SPEAKER.equals(action)) {
            Log.d(TAG, "Speaker toggled from notification action");
            ActiveCallManager.getInstance().toggleSpeaker();

        } else if (ACTION_END_CALL.equals(action)) {
            Log.d(TAG, "End Call clicked from notification action");
            if (manager != null && notificationId != -1) {
                manager.cancel(notificationId);
            }
            CallForegroundService.stopService(context);
            NotificationHelper.cancelActiveCallNotification(context);
            ActiveCallManager.getInstance().endCall();
        }
    }
}
