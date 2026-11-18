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
    private List<String> mediaUrls;
    private String caption;
    private long durationSeconds;
    private int replayCount = 0;
    private boolean isSaved = false;
    private boolean isExpired = false;

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

    public List<String> getMediaUrls() {
        return mediaUrls;
    }

    public void setMediaUrls(List<String> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
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

    public int getReplayCount() {
        return replayCount;
    }

    public void setReplayCount(int replayCount) {
        this.replayCount = replayCount;
    }

    public boolean isSaved() {
        return isSaved;
    }

    public void setSaved(boolean saved) {
        isSaved = saved;
    }

    public boolean isExpired() {
        return isExpired;
    }

    public void setExpired(boolean expired) {
        isExpired = expired;
    }
}
