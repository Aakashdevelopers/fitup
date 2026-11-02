package com.amstudio.fitbond.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.RemoteInput;

import com.amstudio.fitbond.helper.NotificationHelper;
import com.amstudio.fitbond.repository.TencentChatManager;
import com.tencent.imsdk.v2.V2TIMMessage;

/**
 * BroadcastReceiver to handle Direct Inline Replies directly from the Notification Shade (WhatsApp/Instagram style).
 */
public class DirectReplyReceiver extends BroadcastReceiver {

    private static final String TAG = "DirectReplyReceiver";
    public static final String ACTION_DIRECT_REPLY = "com.amstudio.fitbond.ACTION_DIRECT_REPLY";
    public static final String KEY_TEXT_REPLY = "key_text_reply";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DIRECT_REPLY.equals(intent.getAction())) return;

        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput == null) return;

        CharSequence replyCharSequence = remoteInput.getCharSequence(KEY_TEXT_REPLY);
        if (replyCharSequence == null || replyCharSequence.length() == 0) return;

        String replyText = replyCharSequence.toString().trim();
        String senderUid = intent.getStringExtra("extra_sender_uid");
        String senderName = intent.getStringExtra("extra_sender_name");

        if (senderUid == null || senderUid.isEmpty()) return;

        Log.d(TAG, "Direct inline reply received for " + senderName + " (" + senderUid + "): " + replyText);

        // 1. Send the reply text in background via Tencent IM / OneSignal
        TencentChatManager.getInstance().sendTextMessage(senderUid, replyText, new TencentChatManager.MessageCallback() {
            @Override
            public void onSuccess(V2TIMMessage message) {
                Log.d(TAG, "Inline reply sent successfully to " + senderName);
            }

            @Override
            public void onError(int code, String desc) {
                Log.e(TAG, "Failed to send inline reply: " + code + " " + desc);
            }
        });

        // 2. Save self reply into Notification history and refresh notification UI cleanly
        NotificationHelper.addSelfReplyToHistory(context, senderUid, replyText);
        NotificationHelper.showChatNotification(context, senderUid, senderName, null, replyText, System.currentTimeMillis());
    }
}
