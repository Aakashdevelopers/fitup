package com.amstudio.fitbond.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.List;

public class SnapMetadata {

    public static final String MEDIA_TYPE_PHOTO = "PHOTO";
    public static final String MEDIA_TYPE_VIDEO = "VIDEO";

    private String snapId;
    private String senderUid;
    private List<String> recipientUids;
    private String mediaType;
    private String mediaUrl;
    private long durationSeconds;

    @ServerTimestamp
    private Timestamp createdAt;

    public SnapMetadata() {
        // Default constructor required for Firestore deserialization
    }

    public SnapMetadata(String snapId, String senderUid, List<String> recipientUids,
                        String mediaType, String mediaUrl, long durationSeconds) {
        this.snapId = snapId;
        this.senderUid = senderUid;
        this.recipientUids = recipientUids;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.durationSeconds = durationSeconds;
    }

    public String getSnapId() {
        return snapId;
    }

    public void setSnapId(String snapId) {
        this.snapId = snapId;
    }

    public String getSenderUid() {
        return senderUid;
    }

    public void setSenderUid(String senderUid) {
        this.senderUid = senderUid;
    }

    public List<String> getRecipientUids() {
        return recipientUids;
    }

    public void setRecipientUids(List<String> recipientUids) {
        this.recipientUids = recipientUids;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
