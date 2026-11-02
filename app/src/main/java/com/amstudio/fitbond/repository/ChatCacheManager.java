package com.amstudio.fitbond.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.amstudio.fitbond.ChatItem;
import com.amstudio.fitbond.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChatCacheManager {

    private static final String TAG = "ChatCacheManager";
    private static final String PREF_NAME = "chat_cache_pref";
    private static final int MAX_CACHED_MESSAGES = 100;

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveChatMessages(Context context, String currentUid, String chatId, List<ChatMessage> messages) {
        if (context == null || currentUid == null || chatId == null || messages == null) return;

        try {
            JSONArray array = new JSONArray();
            int startIndex = Math.max(0, messages.size() - MAX_CACHED_MESSAGES);
            for (int idx = startIndex; idx < messages.size(); idx++) {
                ChatMessage msg = messages.get(idx);
                if (msg == null || msg.getId() == null || msg.getId().startsWith("demo_")) continue;

                JSONObject json = new JSONObject();
                json.put("id", msg.getId());
                json.put("senderId", msg.getSenderId() != null ? msg.getSenderId() : "");
                json.put("senderName", msg.getSenderName() != null ? msg.getSenderName() : "");
                json.put("text", msg.getText() != null ? msg.getText() : "");
                json.put("messageType", msg.getMessageType() != null ? msg.getMessageType().name() : ChatMessage.MessageType.TEXT_INCOMING.name());
                json.put("snapKind", msg.getSnapKind() != null ? msg.getSnapKind().name() : ChatMessage.SnapKind.NONE.name());
                json.put("status", msg.getStatus() != null ? msg.getStatus().name() : ChatMessage.Status.SENT.name());
                json.put("timestamp", msg.getTimestamp() != null ? msg.getTimestamp() : "");
                json.put("durationText", msg.getDurationText() != null ? msg.getDurationText() : "");
                json.put("mediaUri", msg.getMediaUri() != null ? msg.getMediaUri() : "");
                json.put("isViewed", msg.isViewed());
                json.put("isMissedCall", msg.isMissedCall());
                json.put("isVideoCall", msg.isVideoCall());
                json.put("userReactionEmoji", msg.getUserReactionEmoji() != null ? msg.getUserReactionEmoji() : "");

                if (msg.getReactions() != null) {
                    JSONArray reactionsArray = new JSONArray();
                    for (ChatMessage.Reaction r : msg.getReactions()) {
                        JSONObject rObj = new JSONObject();
                        rObj.put("emoji", r.getEmoji());
                        rObj.put("count", r.getCount());
                        reactionsArray.put(rObj);
                    }
                    json.put("reactions", reactionsArray);
                }

                if (msg.getMediaUris() != null && !msg.getMediaUris().isEmpty()) {
                    JSONArray urisArr = new JSONArray();
                    for (String u : msg.getMediaUris()) {
                        urisArr.put(u);
                    }
                    json.put("mediaUris", urisArr);
                }

                array.put(json);
            }

            String key = "msg_" + currentUid + "_" + chatId;
            getPrefs(context).edit().putString(key, array.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save cached messages for " + chatId, e);
        }
    }

    public static List<ChatMessage> getCachedChatMessages(Context context, String currentUid, String chatId) {
        List<ChatMessage> list = new ArrayList<>();
        if (context == null || currentUid == null || chatId == null) return list;

        try {
            String key = "msg_" + currentUid + "_" + chatId;
            String jsonStr = getPrefs(context).getString(key, null);
            if (jsonStr == null || jsonStr.isEmpty()) return list;

            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                String id = json.optString("id", "");
                if (id.startsWith("demo_")) continue;

                String senderId = json.optString("senderId", "");
                String senderName = json.optString("senderName", "");
                String text = json.optString("text", "");
                String typeStr = json.optString("messageType", ChatMessage.MessageType.TEXT_INCOMING.name());
                String snapStr = json.optString("snapKind", ChatMessage.SnapKind.NONE.name());
                String statusStr = json.optString("status", ChatMessage.Status.SENT.name());
                String timestamp = json.optString("timestamp", "");
                String durationText = json.optString("durationText", "");
                String mediaUri = json.has("mediaUri") ? json.optString("mediaUri", "") : null;
                if (mediaUri != null && mediaUri.isEmpty()) mediaUri = null;
                boolean isViewed = json.optBoolean("isViewed", false);
                boolean isMissedCall = json.optBoolean("isMissedCall", false);
                boolean isVideoCall = json.optBoolean("isVideoCall", false);
                String userReactionEmoji = json.has("userReactionEmoji") ? json.optString("userReactionEmoji", "") : null;
                if (userReactionEmoji != null && userReactionEmoji.isEmpty()) userReactionEmoji = null;

                ChatMessage.MessageType messageType;
                try {
                    messageType = ChatMessage.MessageType.valueOf(typeStr);
                } catch (Exception e) {
                    messageType = ChatMessage.MessageType.TEXT_INCOMING;
                }

                ChatMessage.SnapKind snapKind;
                try {
                    snapKind = ChatMessage.SnapKind.valueOf(snapStr);
                } catch (Exception e) {
                    snapKind = ChatMessage.SnapKind.NONE;
                }

                ChatMessage msg = new ChatMessage(
                        id, senderId, senderName, text, messageType, snapKind,
                        timestamp, durationText, mediaUri, isViewed, isMissedCall, isVideoCall
                );
                msg.setUserReactionEmoji(userReactionEmoji);
                try {
                    msg.setStatus(ChatMessage.Status.valueOf(statusStr));
                } catch (Exception ignored) {}

                if (json.has("reactions")) {
                    JSONArray rArray = json.getJSONArray("reactions");
                    for (int j = 0; j < rArray.length(); j++) {
                        JSONObject rObj = rArray.getJSONObject(j);
                        msg.addReaction(rObj.optString("emoji"), rObj.optInt("count", 1));
                    }
                }

                if (json.has("mediaUris")) {
                    JSONArray uArray = json.getJSONArray("mediaUris");
                    List<String> uris = new ArrayList<>();
                    for (int j = 0; j < uArray.length(); j++) {
                        uris.add(uArray.getString(j));
                    }
                    msg.setMediaUris(uris);
                }

                list.add(msg);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load cached messages for " + chatId, e);
        }

        return list;
    }

    public static void saveRecentChats(Context context, String currentUid, List<ChatItem> chats) {
        if (context == null || currentUid == null || chats == null) return;

        try {
            JSONArray array = new JSONArray();
            for (ChatItem item : chats) {
                if (item == null || item.getId() == null) continue;
                JSONObject json = new JSONObject();
                json.put("id", item.getId());
                json.put("name", item.getName() != null ? item.getName() : "");
                json.put("initials", item.getInitials() != null ? item.getInitials() : "");
                json.put("gradientResId", item.getGradientResId());
                json.put("lastMessage", item.getLastMessage() != null ? item.getLastMessage() : "");
                json.put("time", item.getTime() != null ? item.getTime() : "");
                json.put("unreadCount", item.getUnreadCount());
                json.put("hasStreak", item.hasStreak());
                json.put("streakCount", item.getStreakCount());
                json.put("isOnline", item.isOnline());
                json.put("hasRing", item.hasRing());
                json.put("lastMessageType", item.getLastMessageType());
                array.put(json);
            }

            String key = "chats_" + currentUid;
            getPrefs(context).edit().putString(key, array.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save cached recent chats", e);
        }
    }

    public static List<ChatItem> getCachedRecentChats(Context context, String currentUid) {
        List<ChatItem> list = new ArrayList<>();
        if (context == null || currentUid == null) return list;

        try {
            String key = "chats_" + currentUid;
            String jsonStr = getPrefs(context).getString(key, null);
            if (jsonStr == null || jsonStr.isEmpty()) return list;

            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                String id = json.optString("id");
                String name = json.optString("name");
                String initials = json.optString("initials");
                int gradientResId = json.optInt("gradientResId");
                String lastMessage = json.optString("lastMessage");
                String time = json.optString("time");
                int unreadCount = json.optInt("unreadCount");
                boolean hasStreak = json.optBoolean("hasStreak");
                int streakCount = json.optInt("streakCount");
                boolean isOnline = json.optBoolean("isOnline");
                boolean hasRing = json.optBoolean("hasRing");
                int lastMessageType = json.optInt("lastMessageType", ChatItem.TYPE_TEXT);

                ChatItem item = new ChatItem(
                        id, name, initials, gradientResId, lastMessage, time, unreadCount,
                        hasStreak, streakCount, isOnline, hasRing, lastMessageType
                );
                list.add(item);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load cached recent chats", e);
        }

        return list;
    }
}
