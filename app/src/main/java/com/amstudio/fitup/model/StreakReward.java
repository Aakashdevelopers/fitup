package com.amstudio.fitup.model;

public class StreakReward {
    private String rewardId;
    private int milestone;
    private String rewardType;
    private String rewardValue;
    private String title;
    private String description;
    private String status;
    private Object claimedAt;

    public StreakReward() {}

    public String getRewardId() { return rewardId; }
    public void setRewardId(String rewardId) { this.rewardId = rewardId; }

    public int getMilestone() { return milestone; }
    public void setMilestone(int milestone) { this.milestone = milestone; }

    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }

    public String getRewardValue() { return rewardValue; }
    public void setRewardValue(String rewardValue) { this.rewardValue = rewardValue; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Object getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Object claimedAt) { this.claimedAt = claimedAt; }

    public boolean isClaimed() {
        return "CLAIMED".equalsIgnoreCase(status);
    }

    public boolean isUnlocked() {
        return "UNLOCKED".equalsIgnoreCase(status);
    }
}
