package com.amstudio.fitup.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

public class Friendship {

    private String friendshipId;
    private String user1Uid;
    private String user2Uid;

    @ServerTimestamp
    private Timestamp createdAt;

    public Friendship() {
        // Default constructor required for Firestore deserialization
    }

    public Friendship(String friendshipId, String user1Uid, String user2Uid) {
        this.friendshipId = friendshipId;
        this.user1Uid = user1Uid;
        this.user2Uid = user2Uid;
    }

    public String getFriendshipId() {
        return friendshipId;
    }

    public void setFriendshipId(String friendshipId) {
        this.friendshipId = friendshipId;
    }

    public String getUser1Uid() {
        return user1Uid;
    }

    public void setUser1Uid(String user1Uid) {
        this.user1Uid = user1Uid;
    }

    public String getUser2Uid() {
        return user2Uid;
    }

    public void setUser2Uid(String user2Uid) {
        this.user2Uid = user2Uid;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
