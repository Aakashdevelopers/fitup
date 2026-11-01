package com.amstudio.fitup;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {

    public enum MessageType {
        TEXT_INCOMING,
        TEXT_OUTGOING,
        SNAP_INCOMING,
        SNAP_OUTGOING,
        IMAGE_INCOMING,
        IMAGE_OUTGOING,
        CALL_EVENT
    }

    public enum SnapKind {
        NONE,
        PHOTO,
        VIDEO
    }

    public static class Reaction {
        private final String emoji;
        private final int count;

        public Reaction(String emoji, int count) {
            this.emoji = emoji;
            this.count = count;
        }

        public String getEmoji() {
            return emoji;
        }

        public int getCount() {
            return count;
        }

        public String getDisplayText() {
            return emoji + " " + count;
        }
    }

    private final String id;
    private final String senderId;
    private final String senderName;
    private final String text;
    private final MessageType messageType;
    private final SnapKind snapKind;
    private final String timestamp;
    private final String durationText;
    private final String mediaUri;
    private boolean isViewed;

    private boolean isMissedCall = false;
    private boolean isVideoCall = false;
    private List<Reaction> reactions = new ArrayList<>();
    private List<String> mediaUris = new ArrayList<>();
    private int photoCount = 1;
    private String userReactionEmoji = null;

    public ChatMessage(String id, String senderId, String senderName, String text,
                       MessageType messageType, SnapKind snapKind, String timestamp,
                       String durationText, String mediaUri, boolean isViewed) {
        this(id, senderId, senderName, text, messageType, snapKind, timestamp, durationText, mediaUri, isViewed, false, false);
    }

    public ChatMessage(String id, String senderId, String senderName, String text,
                       MessageType messageType, SnapKind snapKind, String timestamp,
                       String durationText, String mediaUri, boolean isViewed,
                       boolean isMissedCall, boolean isVideoCall) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.messageType = messageType;
        this.snapKind = snapKind;
        this.timestamp = timestamp;
        this.durationText = durationText;
        this.mediaUri = mediaUri;
        this.isViewed = isViewed;
        this.isMissedCall = isMissedCall;
        this.isVideoCall = isVideoCall;
        if (mediaUri != null && !mediaUri.isEmpty()) {
            this.mediaUris.add(mediaUri);
        }
    }

    public String getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getText() {
        return text;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public SnapKind getSnapKind() {
        return snapKind;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getDurationText() {
        return durationText;
    }

    public String getMediaUri() {
        return mediaUri;
    }

    public boolean isViewed() {
        return isViewed;
    }

    public void setViewed(boolean viewed) {
        isViewed = viewed;
    }

    public boolean isSnap() {
        return (messageType == MessageType.SNAP_INCOMING || messageType == MessageType.SNAP_OUTGOING)
                || (snapKind != SnapKind.NONE && messageType != MessageType.IMAGE_INCOMING && messageType != MessageType.IMAGE_OUTGOING);
    }

    public boolean isCall() {
        return messageType == MessageType.CALL_EVENT;
    }

    public boolean isMissedCall() {
        return isMissedCall;
    }

    public boolean isVideoCall() {
        return isVideoCall;
    }

    public boolean isOutgoing() {
        return messageType == MessageType.TEXT_OUTGOING
                || messageType == MessageType.SNAP_OUTGOING
                || messageType == MessageType.IMAGE_OUTGOING;
    }

    public List<Reaction> getReactions() {
        return reactions;
    }

    public void setReactions(List<Reaction> reactions) {
        this.reactions = reactions != null ? reactions : new ArrayList<>();
    }

    public void addReaction(String emoji, int count) {
        if (this.reactions == null) {
            this.reactions = new ArrayList<>();
        }
        this.reactions.add(new Reaction(emoji, count));
    }

    public List<String> getMediaUris() {
        return mediaUris;
    }

    public void setMediaUris(List<String> mediaUris) {
        if (mediaUris != null) {
            this.mediaUris = mediaUris;
            this.photoCount = mediaUris.size();
        }
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    public String getUserReactionEmoji() {
        return userReactionEmoji;
    }

    public void setUserReactionEmoji(String userReactionEmoji) {
        this.userReactionEmoji = userReactionEmoji;
    }
}
