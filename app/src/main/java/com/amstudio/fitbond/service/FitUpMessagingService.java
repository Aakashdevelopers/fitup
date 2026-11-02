package com.amstudio.fitbond.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.amstudio.fitbond.MainActivity;
import com.amstudio.fitbond.R;
import com.amstudio.fitbond.helper.NotificationHelper;
import com.amstudio.fitbond.repository.ActiveCallManager;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class FitUpMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FitUpMessagingService";
    private static final String CHANNEL_ID = "fitup_notifications_channel";
    private static final String CHANNEL_NAME = "Gym Social Notifications";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token received: " + token);
        saveTokenToFirestore(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message received from: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        String title = "FitBond";
        String body = "New message received";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null ? remoteMessage.getNotification().getTitle() : title;
            body = remoteMessage.getNotification().getBody() != null ? remoteMessage.getNotification().getBody() : body;
        } else if (data.size() > 0) {
            title = data.getOrDefault("title", data.getOrDefault("senderName", title));
            body = data.getOrDefault("body", data.getOrDefault("messageText", body));
        }

        String type = data.get("type");
        String senderUid = data.get("senderUid");
        String senderName = data.getOrDefault("senderName", title);
        String senderAvatar = data.getOrDefault("senderAvatar", "");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if ("INCOMING_CALL".equals(type) || "TRTC_CALL_SIGNAL".equals(type)) {
            String action = data.getOrDefault("action", "");
            String callId = data.getOrDefault("callId", "");
            String callerUid = data.getOrDefault("callerUid", senderUid != null ? senderUid : "");
            String callerName = data.getOrDefault("callerName", senderName);
            String callerAvatar = data.getOrDefault("callerAvatar", senderAvatar);
            boolean isVideo = Boolean.parseBoolean(data.getOrDefault("isVideo", "false"));

            if (currentUser == null || (callerUid != null && callerUid.equals(currentUser.getUid()))) {
                Log.w(TAG, "Ignoring self/unauthenticated FCM call notification");
                return;
            }

            if ("CANCEL".equalsIgnoreCase(action) || "CANCELLED".equalsIgnoreCase(action)
                    || "REJECT".equalsIgnoreCase(action) || "REJECTED".equalsIgnoreCase(action)
                    || "HANGUP".equalsIgnoreCase(action) || "END".equalsIgnoreCase(action) || "ENDED".equalsIgnoreCase(action)) {
                NotificationHelper.cancelCallNotification(this, callId);
                if ("CANCEL".equalsIgnoreCase(action) || "CANCELLED".equalsIgnoreCase(action)) {
                    NotificationHelper.showMissedCallNotification(
                            this, callId, callerUid, callerName, callerAvatar, isVideo, System.currentTimeMillis()
                    );
                }
            } else {
                if (callId != null && !callId.isEmpty()) {
                    if (!ActiveCallManager.getInstance().canAcceptIncomingCall(callId)) {
                        Log.w(TAG, "Ignoring FCM incoming call notification for " + callId + ": Active call in progress");
                        return;
                    }

                    FirebaseFirestore.getInstance().collection("calls").document(callId).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    String status = doc.getString("status");
                                    Timestamp createdAt = doc.getTimestamp("createdAt");
                                    long ageMs = (createdAt != null) ? (System.currentTimeMillis() - createdAt.toDate().getTime()) : 0;

                                    if ("CALLING".equalsIgnoreCase(status) && ageMs < 60000) {
                                        NotificationHelper.showIncomingCallNotification(this, callId, callerUid, callerName, callerAvatar, isVideo);
                                    }
                                }
                            });
                }
            }
        } else if ("CHAT_MESSAGE".equals(type) && senderUid != null && !senderUid.isEmpty()) {
            if (currentUser != null && !senderUid.equals(currentUser.getUid())) {
                NotificationHelper.showChatNotification(
                        this, senderUid, senderName, senderAvatar, body, System.currentTimeMillis()
                );
            }
        } else if ("MISSED_CALL".equals(type)) {
            String callId = data.getOrDefault("callId", "");
            String callerUid = data.getOrDefault("callerUid", senderUid != null ? senderUid : "");
            String callerName = data.getOrDefault("callerName", senderName);
            String callerAvatar = data.getOrDefault("callerAvatar", senderAvatar);
            boolean isVideo = Boolean.parseBoolean(data.getOrDefault("isVideo", "false"));

            if (currentUser != null && callerUid != null && !callerUid.equals(currentUser.getUid())) {
                NotificationHelper.showMissedCallNotification(
                        this, callId, callerUid, callerName, callerAvatar, isVideo, System.currentTimeMillis()
                );
            }
        } else {
            showNotification(title, body, data);
        }
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for friend requests, chats, and snaps");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }

    public static void saveCurrentDeviceToken() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String token = task.getResult();
                saveTokenToFirestoreForUid(uid, token);
            }
        });
    }

    private static void saveTokenToFirestore(String token) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            saveTokenToFirestoreForUid(currentUser.getUid(), token);
        }
    }

    private static void saveTokenToFirestoreForUid(String uid, String token) {
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("token", token);
        tokenData.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("deviceTokens")
                .document(token)
                .set(tokenData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Device token updated in Firestore for user " + uid))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update device token in Firestore", e));
    }
}
