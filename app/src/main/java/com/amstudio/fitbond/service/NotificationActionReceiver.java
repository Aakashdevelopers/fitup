package com.amstudio.fitbond.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.amstudio.fitbond.helper.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Receiver to handle "Mark as read" and "Mute" actions from Chat Notifications.
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "NotificationAction";
    public static final String ACTION_MARK_AS_READ = "com.amstudio.fitbond.ACTION_MARK_AS_READ";
    public static final String ACTION_MUTE_CONVERSATION = "com.amstudio.fitbond.ACTION_MUTE_CONVERSATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        int notificationId = intent.getIntExtra("notification_id", -1);
        String senderUid = intent.getStringExtra("extra_sender_uid");

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && notificationId != -1) {
            manager.cancel(notificationId);
        }

        if (senderUid != null && !senderUid.isEmpty()) {
            NotificationHelper.clearConversationHistory(context, senderUid);
        }

        if (ACTION_MARK_AS_READ.equals(action)) {
            Log.d(TAG, "Marking conversation as read for sender: " + senderUid);
            markConversationAsRead(senderUid);
            Toast.makeText(context, "Marked as read", Toast.LENGTH_SHORT).show();

        } else if (ACTION_MUTE_CONVERSATION.equals(action)) {
            Log.d(TAG, "Muting conversation for sender: " + senderUid);
            muteSender(context, senderUid);
            Toast.makeText(context, "Muted notifications", Toast.LENGTH_SHORT).show();
        }
    }

    private void markConversationAsRead(String senderUid) {
        if (senderUid == null || senderUid.isEmpty()) return;
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String myUid = currentUser.getUid();
        String conversationId = myUid.compareTo(senderUid) < 0 ? myUid + "_" + senderUid : senderUid + "_" + myUid;

        FirebaseFirestore.getInstance()
                .collection("chats")
                .document(conversationId)
                .collection("messages")
                .whereEqualTo("senderUid", senderUid)
                .whereEqualTo("status", "DELIVERED")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        querySnapshot.getDocuments().forEach(doc -> doc.getReference().update("status", "READ"));
                    }
                });
    }

    private void muteSender(Context context, String senderUid) {
        if (context == null || senderUid == null || senderUid.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("fitbond_muted_senders", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("muted_" + senderUid, true).apply();
    }

    public static boolean isSenderMuted(Context context, String senderUid) {
        if (context == null || senderUid == null || senderUid.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences("fitbond_muted_senders", Context.MODE_PRIVATE);
        return prefs.getBoolean("muted_" + senderUid, false);
    }
}
