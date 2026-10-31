package com.amstudio.fitup.model;

public class StreakHistoryItem {
    private String date;
    private int streakValue;
    private Object checkedInAt;
    private String activityType;

    public StreakHistoryItem() {}

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getStreakValue() { return streakValue; }
    public void setStreakValue(int streakValue) { this.streakValue = streakValue; }

    public Object getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Object checkedInAt) { this.checkedInAt = checkedInAt; }

    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
}
