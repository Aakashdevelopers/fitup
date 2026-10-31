package com.amstudio.fitup.model;

import java.util.List;

public class StreakStatus {
    private String uid;
    private int currentStreak;
    private int longestStreak;
    private int totalCheckIns;
    private String lastCheckInDate;
    private String todayDate;
    private boolean isCheckedInToday;
    private String timezone;
    private NextMilestoneInfo nextMilestoneInfo;
    private List<StreakReward> rewards;

    public StreakStatus() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }

    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }

    public int getTotalCheckIns() { return totalCheckIns; }
    public void setTotalCheckIns(int totalCheckIns) { this.totalCheckIns = totalCheckIns; }

    public String getLastCheckInDate() { return lastCheckInDate; }
    public void setLastCheckInDate(String lastCheckInDate) { this.lastCheckInDate = lastCheckInDate; }

    public String getTodayDate() { return todayDate; }
    public void setTodayDate(String todayDate) { this.todayDate = todayDate; }

    public boolean isCheckedInToday() { return isCheckedInToday; }
    public void setCheckedInToday(boolean checkedInToday) { isCheckedInToday = checkedInToday; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public NextMilestoneInfo getNextMilestoneInfo() { return nextMilestoneInfo; }
    public void setNextMilestoneInfo(NextMilestoneInfo nextMilestoneInfo) { this.nextMilestoneInfo = nextMilestoneInfo; }

    public List<StreakReward> getRewards() { return rewards; }
    public void setRewards(List<StreakReward> rewards) { this.rewards = rewards; }
}
