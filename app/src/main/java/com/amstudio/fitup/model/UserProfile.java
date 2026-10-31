package com.amstudio.fitup.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

public class UserProfile {

    private String uid;
    private String email;
    private String username;
    private String normalizedUsername;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private String gender;
    private String dateOfBirth;
    private String location;
    private long friendsCount;

    @ServerTimestamp
    private Timestamp createdAt;

    @ServerTimestamp
    private Timestamp updatedAt;

    public UserProfile() {
        // Default constructor required for Firestore deserialization
    }

    public UserProfile(String uid, String email, String username, String displayName, String bio, String avatarUrl) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.normalizedUsername = username != null ? username.toLowerCase().trim() : null;
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.friendsCount = 0;
    }

    public UserProfile(String uid, String email, String username, String displayName, String bio,
                       String avatarUrl, String gender, String dateOfBirth, String location) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.normalizedUsername = username != null ? username.toLowerCase().trim() : null;
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.location = location;
        this.friendsCount = 0;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        if (username != null) {
            this.normalizedUsername = username.toLowerCase().trim();
        }
    }

    public String getNormalizedUsername() {
        if (normalizedUsername == null && username != null) {
            return username.toLowerCase().trim();
        }
        return normalizedUsername;
    }

    public void setNormalizedUsername(String normalizedUsername) {
        this.normalizedUsername = normalizedUsername;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public long getFriendsCount() {
        return friendsCount;
    }

    public void setFriendsCount(long friendsCount) {
        this.friendsCount = friendsCount;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
