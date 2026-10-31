package com.amstudio.fitup;

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
        return snapKind != SnapKind.NONE;
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
}
