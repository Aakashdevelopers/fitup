package com.amstudio.fitbond.service;

import android.content.Context;
import android.util.Log;

import com.amstudio.fitbond.helper.NotificationHelper;
import com.amstudio.fitbond.repository.ActiveCallManager;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.INotificationServiceExtension;

import org.json.JSONObject;

public class CustomNotificationServiceExtension implements INotificationServiceExtension {
    private static final String TAG = "CustomNotificationExt";

    @Override
    public void onNotificationReceived(INotificationReceivedEvent event) {
        if (event == null || event.getNotification() == null) return;

        Log.d(TAG, "OneSignal notification received in ServiceExtension");
        JSONObject data = event.getNotification().getAdditionalData();

        if (data != null) {
            String type = data.optString("type");
            Context context = event.getContext();

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

            if ("INCOMING_CALL".equals(type) || "TRTC_CALL_SIGNAL".equals(type)) {
                String action = data.optString("action");
                String callId = data.optString("callId");
                String callerUid = data.optString("callerUid");
                String callerName = data.optString("callerName");
                String callerAvatar = data.optString("callerAvatar");
                boolean isVideo = Boolean.parseBoolean(data.optString("isVideo", "false"));

                if (currentUser == null) {
                    Log.w(TAG, "Ignoring call notification: User not authenticated locally");
                    event.preventDefault();
                    return;
                }

                if (callerUid != null && callerUid.equals(currentUser.getUid())) {
                    Log.w(TAG, "Ignoring self-generated call notification");
                    event.preventDefault();
                    return;
                }

                if ("CANCEL".equalsIgnoreCase(action) || "CANCELLED".equalsIgnoreCase(action)
                        || "REJECT".equalsIgnoreCase(action) || "REJECTED".equalsIgnoreCase(action)
                        || "HANGUP".equalsIgnoreCase(action) || "END".equalsIgnoreCase(action) || "ENDED".equalsIgnoreCase(action)) {
                    NotificationHelper.cancelCallNotification(context, callId);
                    if ("CANCEL".equalsIgnoreCase(action) || "CANCELLED".equalsIgnoreCase(action)) {
                        NotificationHelper.showMissedCallNotification(
                                context, callId, callerUid, callerName, callerAvatar, isVideo, System.currentTimeMillis()
                        );
                    }
                } else {
                    if (callId != null && !callId.isEmpty()) {
                        if (!ActiveCallManager.getInstance().canAcceptIncomingCall(callId)) {
                            Log.w(TAG, "Ignoring incoming call notification for " + callId + ": Active call in progress");
                            event.preventDefault();
                            return;
                        }

                        // Validate against Firestore backend as source of truth
                        FirebaseFirestore.getInstance().collection("calls").document(callId).get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) {
                                        String status = doc.getString("status");
                                        Timestamp createdAt = doc.getTimestamp("createdAt");
                                        long ageMs = (createdAt != null) ? (System.currentTimeMillis() - createdAt.toDate().getTime()) : 0;

                                        if ("CALLING".equalsIgnoreCase(status) && (createdAt == null || ageMs < 60000)) {
                                            NotificationHelper.showIncomingCallNotification(
                                                    context, callId, callerUid, callerName, callerAvatar, isVideo
                                            );
                                        } else {
                                            Log.w(TAG, "Call " + callId + " is no longer ringing (status=" + status + ", age=" + (ageMs / 1000) + "s)");
                                            NotificationHelper.cancelCallNotification(context, callId);
                                        }
                                    } else {
                                        Log.w(TAG, "Call document " + callId + " does not exist in Firestore");
                                        NotificationHelper.cancelCallNotification(context, callId);
                                    }
                                });
                    }
                }
                event.preventDefault();

            } else if ("CHAT_MESSAGE".equals(type)) {
                String senderUid = data.optString("senderUid");
                String senderName = data.optString("senderName");
                String senderAvatar = data.optString("senderAvatar");
                String body = event.getNotification().getBody();

                if (currentUser != null && senderUid != null && !senderUid.equals(currentUser.getUid())) {
                    NotificationHelper.showChatNotification(
                            context, senderUid, senderName, senderAvatar, body, System.currentTimeMillis()
                    );
                }
                event.preventDefault();

            } else if ("MISSED_CALL".equals(type)) {
                String callId = data.optString("callId");
                String callerUid = data.optString("callerUid");
                String callerName = data.optString("callerName");
                String callerAvatar = data.optString("callerAvatar");
                boolean isVideo = Boolean.parseBoolean(data.optString("isVideo", "false"));

                if (currentUser != null && callerUid != null && !callerUid.equals(currentUser.getUid())) {
                    NotificationHelper.showMissedCallNotification(
                            context, callId, callerUid, callerName, callerAvatar, isVideo, System.currentTimeMillis()
                    );
                }
                event.preventDefault();
            }
        }
    }
}
