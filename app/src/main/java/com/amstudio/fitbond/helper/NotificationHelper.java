package com.amstudio.fitbond.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.amstudio.fitbond.IndividualChatActivity;
import com.amstudio.fitbond.MainActivity;
import com.amstudio.fitbond.R;
import com.amstudio.fitbond.TRTCCallActivity;
import com.amstudio.fitbond.repository.ActiveCallManager;
import com.amstudio.fitbond.service.CallActionReceiver;
import com.amstudio.fitbond.service.DirectReplyReceiver;
import com.amstudio.fitbond.service.NotificationActionReceiver;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced NotificationHelper supporting WhatsApp/Instagram-style Chat MessagingStyle,
 * incoming Voice and Video Call notifications, Missed Call notifications,
 * avatar caching, and notification grouping.
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    public static final String CHANNEL_CHAT_ID = "fitbond_chat_messages_v3";
    public static final String CHANNEL_CHAT_NAME = "FitBond Messages";

    public static final String CHANNEL_CALL_ID = "fitbond_incoming_call_channel_v3";
    public static final String CHANNEL_CALL_NAME = "FitBond Incoming Calls";

    public static final String CHANNEL_ACTIVE_CALL_ID = "fitbond_active_call_channel_v1";
    public static final String CHANNEL_ACTIVE_CALL_NAME = "FitBond Active Calls";
    private static final int ACTIVE_CALL_NOTIFICATION_ID = 20002;

    public static final String CHANNEL_MISSED_CALL_ID = "fitbond_missed_calls_v2";
    public static final String CHANNEL_MISSED_CALL_NAME = "FitBond Missed Calls";

    private static final Set<String> processedMissedCallIds = Collections.synchronizedSet(new HashSet<>());

    private static final String GROUP_KEY_CHAT = "com.amstudio.fitbond.CHAT_MESSAGES_GROUP";
    private static final int SUMMARY_NOTIFICATION_ID = 10001;
    private static final String PREF_HISTORY_NAME = "fitbond_chat_notification_history";

    public static class ChatMessageItem {
        public String text;
        public long timestamp;
        public boolean isSelf;

        public ChatMessageItem(String text, long timestamp, boolean isSelf) {
            this.text = text;
            this.timestamp = timestamp;
            this.isSelf = isSelf;
        }
    }

    // ==========================================
    // 1. CHAT NOTIFICATION IMPLEMENTATION
    // ==========================================

    public static void showChatNotification(Context context, String senderUid, String senderName, String messageText) {
        showChatNotification(context, senderUid, senderName, null, messageText, System.currentTimeMillis());
    }

    public static void showChatNotification(Context context, String senderUid, String senderName,
                                            String senderAvatar, String messageText, long timestamp) {
        if (context == null || senderUid == null || senderUid.isEmpty()) return;

        // Check if sender is muted
        if (NotificationActionReceiver.isSenderMuted(context, senderUid)) {
            Log.d(TAG, "Sender " + senderUid + " is muted, ignoring chat notification.");
            return;
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        // 1. Create Notification Channel with High Importance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_CHAT_ID,
                    CHANNEL_CHAT_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for private chat messages with direct inline reply");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }

        long time = timestamp > 0 ? timestamp : System.currentTimeMillis();
        saveMessageToHistory(context, senderUid, messageText, time, false);
        List<ChatMessageItem> history = getMessagesHistory(context, senderUid);

        int notificationId = Math.abs(senderUid.hashCode());

        // 2. Open IndividualChatActivity on Notification Click
        Intent chatIntent = new Intent(context, IndividualChatActivity.class);
        chatIntent.putExtra(IndividualChatActivity.EXTRA_CHAT_ID, senderUid);
        chatIntent.putExtra(IndividualChatActivity.EXTRA_CHAT_NAME, senderName);
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent chatPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 3. Inline Reply Action (RemoteInput)
        RemoteInput remoteInput = new RemoteInput.Builder(DirectReplyReceiver.KEY_TEXT_REPLY)
                .setLabel("Reply to " + (senderName != null ? senderName : "message") + "...")
                .build();

        Intent replyIntent = new Intent(context, DirectReplyReceiver.class);
        replyIntent.setAction(DirectReplyReceiver.ACTION_DIRECT_REPLY);
        replyIntent.putExtra("extra_sender_uid", senderUid);
        replyIntent.putExtra("extra_sender_name", senderName);
        replyIntent.putExtra("notification_id", notificationId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 10,
                replyIntent,
                flags
        );

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_bell,
                "Reply",
                replyPendingIntent
        ).addRemoteInput(remoteInput)
         .setAllowGeneratedReplies(true)
         .build();

        // 4. Mark as Read Action
        Intent markReadIntent = new Intent(context, NotificationActionReceiver.class);
        markReadIntent.setAction(NotificationActionReceiver.ACTION_MARK_AS_READ);
        markReadIntent.putExtra("extra_sender_uid", senderUid);
        markReadIntent.putExtra("notification_id", notificationId);

        PendingIntent markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 20,
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Action markReadAction = new NotificationCompat.Action.Builder(
                0,
                "Mark as read",
                markReadPendingIntent
        ).build();

        // 5. Mute Conversation Action
        Intent muteIntent = new Intent(context, NotificationActionReceiver.class);
        muteIntent.setAction(NotificationActionReceiver.ACTION_MUTE_CONVERSATION);
        muteIntent.putExtra("extra_sender_uid", senderUid);
        muteIntent.putExtra("notification_id", notificationId);

        PendingIntent mutePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 30,
                muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Action muteAction = new NotificationCompat.Action.Builder(
                0,
                "Mute",
                mutePendingIntent
        ).build();

        // 6. Load Sender Avatar & Build MessagingStyle
        Bitmap avatarBitmap = getAvatarBitmap(context, senderAvatar, senderName);

        Person senderPerson = new Person.Builder()
                .setName(senderName != null ? senderName : "FitBond Friend")
                .setKey(senderUid)
                .setIcon(IconCompat.createWithBitmap(avatarBitmap))
                .build();

        Person selfPerson = new Person.Builder()
                .setName("You")
                .build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(selfPerson)
                .setConversationTitle(senderName);

        for (ChatMessageItem item : history) {
            Person person = item.isSelf ? selfPerson : senderPerson;
            messagingStyle.addMessage(item.text != null ? item.text : "", item.timestamp, person);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_CHAT_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setLargeIcon(avatarBitmap)
                .setStyle(messagingStyle)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setGroup(GROUP_KEY_CHAT)
                .setNumber(history.size())
                .setContentIntent(chatPendingIntent)
                .addAction(replyAction)
                .addAction(markReadAction)
                .addAction(muteAction)
                .setAutoCancel(true);

        notificationManager.notify(notificationId, notificationBuilder.build());

        // Update Notification Group Summary
        updateChatSummaryNotification(context, notificationManager);
    }

    private static void updateChatSummaryNotification(Context context, NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent summaryIntent = new Intent(context, MainActivity.class);
            summaryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent summaryPendingIntent = PendingIntent.getActivity(
                    context,
                    SUMMARY_NOTIFICATION_ID,
                    summaryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(context, CHANNEL_CHAT_ID)
                    .setSmallIcon(R.drawable.ic_bell)
                    .setStyle(new NotificationCompat.InboxStyle()
                            .setSummaryText("FitBond Messages"))
                    .setGroup(GROUP_KEY_CHAT)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setContentIntent(summaryPendingIntent);

            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryBuilder.build());
        }
    }

    // ==========================================
    // 2. INCOMING CALL NOTIFICATION IMPLEMENTATION
    // ==========================================

    public static void showIncomingCallNotification(Context context, String callId, String callerUid,
                                                     String callerName, String callerAvatar, boolean isVideo) {
        if (context == null) return;

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        int notificationId = (callId != null && !callId.isEmpty()) ? Math.abs(callId.hashCode()) : (int) System.currentTimeMillis();

        // 1. High-Priority Call Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_CALL_ID,
                    CHANNEL_CALL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Full-screen notifications for incoming video and audio calls");
            channel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
            );
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }

        // 2. Full-Screen Intent -> TRTCCallActivity
        Intent fullScreenIntent = new Intent(context, TRTCCallActivity.class);
        fullScreenIntent.putExtra("extra_call_id", callId);
        fullScreenIntent.putExtra("extra_target_uid", callerUid);
        fullScreenIntent.putExtra("extra_user_name", callerName);
        fullScreenIntent.putExtra("extra_avatar_url", callerAvatar);
        fullScreenIntent.putExtra("extra_is_video", isVideo);
        fullScreenIntent.putExtra("extra_is_incoming", true);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 3. ACCEPT Call PendingIntent -> Activity PendingIntent to guarantee launching activity in all app states
        Intent acceptIntent = new Intent(context, TRTCCallActivity.class);
        acceptIntent.setAction(CallActionReceiver.ACTION_ACCEPT_CALL);
        acceptIntent.putExtra(TRTCCallActivity.EXTRA_CALL_ID, callId);
        acceptIntent.putExtra(TRTCCallActivity.EXTRA_TARGET_UID, callerUid);
        acceptIntent.putExtra(TRTCCallActivity.EXTRA_USER_NAME, callerName);
        acceptIntent.putExtra(TRTCCallActivity.EXTRA_AVATAR_URL, callerAvatar);
        acceptIntent.putExtra(TRTCCallActivity.EXTRA_IS_VIDEO, isVideo);
        acceptIntent.putExtra(TRTCCallActivity.EXTRA_IS_INCOMING, true);
        acceptIntent.putExtra(TRTCCallActivity.EXTRA_AUTO_ACCEPT, true);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent acceptPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 1,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 4. DECLINE Call PendingIntent
        Intent declineIntent = new Intent(context, CallActionReceiver.class);
        declineIntent.setAction(CallActionReceiver.ACTION_DECLINE_CALL);
        declineIntent.putExtra("notification_id", notificationId);
        declineIntent.putExtra("extra_call_id", callId);
        declineIntent.putExtra("extra_target_uid", callerUid);

        PendingIntent declinePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Load Avatar Bitmap
        Bitmap avatarBitmap = getAvatarBitmap(context, callerAvatar, callerName);

        // 5. Custom RemoteViews Layout for incoming call banner
        RemoteViews customView = new RemoteViews(
                context.getPackageName(),
                R.layout.notification_incoming_call
        );

        String displayName = (callerName != null && !callerName.isEmpty()) ? callerName : "Incoming Call";
        String callTypeText = isVideo ? "Incoming Video Call..." : "Incoming Voice Call...";

        customView.setTextViewText(R.id.tv_caller_name, displayName);
        customView.setTextViewText(R.id.tv_call_type, callTypeText);
        customView.setImageViewBitmap(R.id.iv_caller_avatar, avatarBitmap);
        customView.setOnClickPendingIntent(R.id.btn_accept_call, acceptPendingIntent);
        customView.setOnClickPendingIntent(R.id.btn_decline_call, declinePendingIntent);

        // 6. Build High-Priority Call Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_CALL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setLargeIcon(avatarBitmap)
                .setContentTitle(displayName)
                .setContentText(callTypeText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setOngoing(true)
                .setAutoCancel(true);

        // Android 12+ (API 31+) System CallStyle integration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person callerPerson = new Person.Builder()
                    .setName(displayName)
                    .setIcon(IconCompat.createWithBitmap(avatarBitmap))
                    .setImportant(true)
                    .build();

            NotificationCompat.CallStyle callStyle = NotificationCompat.CallStyle.forIncomingCall(
                    callerPerson,
                    declinePendingIntent,
                    acceptPendingIntent
            );
            builder.setStyle(callStyle);
        } else {
            builder.setCustomContentView(customView)
                    .setCustomBigContentView(customView)
                    .setCustomHeadsUpContentView(customView)
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle());
        }

        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Failed to post incoming CallStyle notification, falling back to custom view style", e);
            try {
                builder.setCustomContentView(customView)
                        .setCustomBigContentView(customView)
                        .setCustomHeadsUpContentView(customView)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle());
                notificationManager.notify(notificationId, builder.build());
            } catch (Exception ex) {
                Log.e(TAG, "Error posting fallback incoming call notification", ex);
            }
        }
    }

    public static void cancelCallNotification(Context context) {
        cancelCallNotification(context, null);
    }

    public static void cancelCallNotification(Context context, String callId) {
        if (context == null) return;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (callId != null && !callId.isEmpty()) {
            notificationManager.cancel(Math.abs(callId.hashCode()));
        } else {
            notificationManager.cancelAll();
        }
    }

    public static int getNotificationIdForCall(String callId) {
        if (callId != null && !callId.isEmpty()) {
            return Math.abs(callId.hashCode());
        }
        return ACTIVE_CALL_NOTIFICATION_ID;
    }

    public static Notification buildActiveCallNotification(Context context, String callId, String targetUid,
                                                           String userName, String avatarUrl, boolean isVideo,
                                                           boolean isMuted, boolean isSpeakerOn) {
        if (context == null) return null;

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ACTIVE_CALL_ID,
                    CHANNEL_ACTIVE_CALL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Ongoing call controls and status bar notification");
            channel.setSound(null, null);
            channel.enableVibration(false);
            notificationManager.createNotificationChannel(channel);
        }

        int notificationId = getNotificationIdForCall(callId);
        Bitmap avatarBitmap = getAvatarBitmap(context, avatarUrl, userName);

        Intent contentIntent = new Intent(context, TRTCCallActivity.class);
        contentIntent.putExtra(TRTCCallActivity.EXTRA_CALL_ID, callId);
        contentIntent.putExtra(TRTCCallActivity.EXTRA_TARGET_UID, targetUid);
        contentIntent.putExtra(TRTCCallActivity.EXTRA_USER_NAME, userName);
        contentIntent.putExtra(TRTCCallActivity.EXTRA_AVATAR_URL, avatarUrl);
        contentIntent.putExtra(TRTCCallActivity.EXTRA_IS_VIDEO, isVideo);
        contentIntent.putExtra(TRTCCallActivity.EXTRA_IS_INCOMING, false);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent muteIntent = new Intent(context, CallActionReceiver.class);
        muteIntent.setAction(CallActionReceiver.ACTION_TOGGLE_MUTE);
        muteIntent.putExtra("extra_call_id", callId);
        muteIntent.putExtra("notification_id", notificationId);
        PendingIntent mutePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent speakerIntent = new Intent(context, CallActionReceiver.class);
        speakerIntent.setAction(CallActionReceiver.ACTION_TOGGLE_SPEAKER);
        speakerIntent.putExtra("extra_call_id", callId);
        speakerIntent.putExtra("notification_id", notificationId);
        PendingIntent speakerPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                speakerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent endIntent = new Intent(context, CallActionReceiver.class);
        endIntent.setAction(CallActionReceiver.ACTION_END_CALL);
        endIntent.putExtra("extra_call_id", callId);
        endIntent.putExtra("notification_id", notificationId);
        PendingIntent endPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = (userName != null && !userName.isEmpty()) ? userName : "Active Call";
        ActiveCallManager.CallState state = ActiveCallManager.getInstance().getCallState();
        long connectedAt = ActiveCallManager.getInstance().getConnectedAtMs();
        boolean isConnected = (state == ActiveCallManager.CallState.CONNECTED && connectedAt > 0);

        String statusText;
        if (isConnected) {
            statusText = (isVideo ? "Active Video Call" : "Active Voice Call") + (isMuted ? " (Muted)" : "");
        } else {
            statusText = "Connecting...";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ACTIVE_CALL_ID)
                .setSmallIcon(R.drawable.ic_phone_call)
                .setLargeIcon(avatarBitmap)
                .setContentTitle(title)
                .setContentText(statusText)
                .setSubText(isVideo ? "Video Call" : "Voice Call")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setUsesChronometer(isConnected)
                .setShowWhen(isConnected)
                .setContentIntent(contentPendingIntent)
                .setFullScreenIntent(contentPendingIntent, false)
                .addAction(
                        isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on,
                        isMuted ? "Unmute" : "Mute",
                        mutePendingIntent
                )
                .addAction(
                        isSpeakerOn ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off,
                        isSpeakerOn ? "Earpiece" : "Speaker",
                        speakerPendingIntent
                )
                .addAction(
                        R.drawable.ic_call_end,
                        "End Call",
                        endPendingIntent
                );

        if (isConnected) {
            builder.setWhen(connectedAt);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person callerPerson = new Person.Builder()
                    .setName(title)
                    .setIcon(IconCompat.createWithBitmap(avatarBitmap))
                    .setImportant(true)
                    .build();

            NotificationCompat.CallStyle callStyle = NotificationCompat.CallStyle.forOngoingCall(
                    callerPerson,
                    endPendingIntent
            );
            builder.setStyle(callStyle);
        }

        return builder.build();
    }

    public static void showActiveCallNotification(Context context, String callId, String targetUid,
                                                   String userName, String avatarUrl, boolean isVideo,
                                                   boolean isMuted, boolean isSpeakerOn) {
        if (context == null) return;

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        int notificationId = getNotificationIdForCall(callId);
        Notification notification = buildActiveCallNotification(context, callId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeakerOn);

        if (notification != null) {
            try {
                notificationManager.notify(notificationId, notification);
            } catch (Exception e) {
                Log.e(TAG, "Error showing active call notification", e);
            }
        }
    }

    public static void cancelActiveCallNotification(Context context) {
        cancelActiveCallNotification(context, null);
    }

    public static void cancelActiveCallNotification(Context context, String callId) {
        if (context == null) return;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID);
            if (callId != null && !callId.isEmpty()) {
                notificationManager.cancel(getNotificationIdForCall(callId));
            }
        }
    }

    // ==========================================
    // 3. MISSED CALL NOTIFICATION IMPLEMENTATION
    // ==========================================

    public static void showMissedCallNotification(Context context, String callerUid, String callerName,
                                                  String callerAvatar, boolean isVideo, long timestamp) {
        showMissedCallNotification(context, null, callerUid, callerName, callerAvatar, isVideo, timestamp);
    }

    public static void showMissedCallNotification(Context context, String callId, String callerUid, String callerName,
                                                  String callerAvatar, boolean isVideo, long timestamp) {
        if (context == null) return;

        if (callId != null && !callId.isEmpty()) {
            if (processedMissedCallIds.contains(callId)) {
                Log.d(TAG, "Ignoring duplicate missed call notification for callId: " + callId);
                return;
            }
            processedMissedCallIds.add(callId);
            cancelCallNotification(context, callId);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        // 1. Create Silent & Vibration-Free Missed Call Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_MISSED_CALL_ID,
                    CHANNEL_MISSED_CALL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for missed audio and video calls");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setVibrationPattern(new long[]{0L});
            notificationManager.createNotificationChannel(channel);
        }

        int notificationId = (callId != null && !callId.isEmpty())
                ? Math.abs((callId + "_missed").hashCode())
                : ((callerUid != null && !callerUid.isEmpty()) ? Math.abs((callerUid + "_missed").hashCode()) : (int) System.currentTimeMillis());

        Bitmap avatarBitmap = getAvatarBitmap(context, callerAvatar, callerName);

        // Tap opens chat screen
        Intent chatIntent = new Intent(context, IndividualChatActivity.class);
        chatIntent.putExtra(IndividualChatActivity.EXTRA_CHAT_ID, callerUid);
        chatIntent.putExtra(IndividualChatActivity.EXTRA_CHAT_NAME, callerName);
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent chatPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = (callerName != null && !callerName.isEmpty()) ? callerName : "Missed Call";
        String contentText = isVideo ? "Missed video call" : "Missed voice call";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MISSED_CALL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setLargeIcon(avatarBitmap)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(isVideo ? "Video Call" : "Voice Call")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setWhen(timestamp > 0 ? timestamp : System.currentTimeMillis())
                .setShowWhen(true)
                .setSound(null)
                .setVibrate(new long[]{0L})
                .setDefaults(0)
                .setContentIntent(chatPendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(notificationId, builder.build());
    }

    // ==========================================
    // 4. AVATAR BITMAP LOADER & FALLBACK GENERATOR
    // ==========================================

    public static Bitmap getAvatarBitmap(Context context, String avatarUrl, String displayName) {
        if (context == null) return null;

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            try {
                Bitmap loadedBitmap = Glide.with(context.getApplicationContext())
                        .asBitmap()
                        .load(avatarUrl)
                        .circleCrop()
                        .submit(128, 128)
                        .get(2, TimeUnit.SECONDS);

                if (loadedBitmap != null) {
                    return loadedBitmap;
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not fetch profile avatar bitmap from URL: " + avatarUrl, e);
            }
        }

        return createDefaultAvatarBitmap(context, displayName);
    }

    public static Bitmap createDefaultAvatarBitmap(Context context, String displayName) {
        int size = 128;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.parseColor("#1E2230"));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        borderPaint.setColor(Color.parseColor("#00E676"));
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, borderPaint);

        if (displayName != null && !displayName.trim().isEmpty()) {
            String initial = displayName.trim().substring(0, 1).toUpperCase();
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(52f);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);

            float yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
            canvas.drawText(initial, size / 2f, yPos, textPaint);
        } else {
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_bell);
            if (drawable != null) {
                drawable.setBounds(32, 32, size - 32, size - 32);
                drawable.draw(canvas);
            }
        }
        return bitmap;
    }

    // ==========================================
    // 5. MESSAGE HISTORY BUFFER PREFERENCES
    // ==========================================

    public static synchronized void saveMessageToHistory(Context context, String senderUid, String text, long timestamp, boolean isSelf) {
        if (context == null || senderUid == null || senderUid.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF_HISTORY_NAME, Context.MODE_PRIVATE);
        String rawJson = prefs.getString("history_" + senderUid, "[]");

        try {
            JSONArray array = new JSONArray(rawJson);
            JSONObject obj = new JSONObject();
            obj.put("text", text != null ? text : "");
            obj.put("timestamp", timestamp);
            obj.put("isSelf", isSelf);
            array.put(obj);

            // Keep max 10 most recent messages per conversation
            while (array.length() > 10) {
                array.remove(0);
            }

            prefs.edit().putString("history_" + senderUid, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving message to notification history: " + e.getMessage(), e);
        }
    }

    public static synchronized void addSelfReplyToHistory(Context context, String senderUid, String replyText) {
        saveMessageToHistory(context, senderUid, replyText, System.currentTimeMillis(), true);
    }

    public static synchronized List<ChatMessageItem> getMessagesHistory(Context context, String senderUid) {
        List<ChatMessageItem> list = new ArrayList<>();
        if (context == null || senderUid == null || senderUid.isEmpty()) return list;

        SharedPreferences prefs = context.getSharedPreferences(PREF_HISTORY_NAME, Context.MODE_PRIVATE);
        String rawJson = prefs.getString("history_" + senderUid, "[]");

        try {
            JSONArray array = new JSONArray(rawJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String text = obj.optString("text");
                long timestamp = obj.optLong("timestamp", System.currentTimeMillis());
                boolean isSelf = obj.optBoolean("isSelf", false);
                list.add(new ChatMessageItem(text, timestamp, isSelf));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading notification history: " + e.getMessage(), e);
        }

        return list;
    }

    public static synchronized void clearConversationHistory(Context context, String senderUid) {
        if (context == null || senderUid == null || senderUid.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_HISTORY_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove("history_" + senderUid).apply();
    }
}
