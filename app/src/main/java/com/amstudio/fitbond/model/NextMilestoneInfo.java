package com.amstudio.fitbond.model;

public class NextMilestoneInfo {
    private int nextMilestone;
    private String nextMilestoneTitle;
    private String nextMilestoneReward;
    private int daysRemaining;
    private int progressPercent;

    public NextMilestoneInfo() {}

    public int getNextMilestone() { return nextMilestone; }
    public void setNextMilestone(int nextMilestone) { this.nextMilestone = nextMilestone; }

    public String getNextMilestoneTitle() { return nextMilestoneTitle; }
    public void setNextMilestoneTitle(String nextMilestoneTitle) { this.nextMilestoneTitle = nextMilestoneTitle; }

    public String getNextMilestoneReward() { return nextMilestoneReward; }
    public void setNextMilestoneReward(String nextMilestoneReward) { this.nextMilestoneReward = nextMilestoneReward; }

    public int getDaysRemaining() { return daysRemaining; }
    public void setDaysRemaining(int daysRemaining) { this.daysRemaining = daysRemaining; }

    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }
}
