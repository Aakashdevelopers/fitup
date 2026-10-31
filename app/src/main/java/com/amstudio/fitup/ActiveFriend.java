package com.amstudio.fitup;

public class ActiveFriend {
    private final String id;
    private final String name;
    private final String initials;
    private final int gradientResId;
    private final boolean isOnline;

    public ActiveFriend(String id, String name, String initials, int gradientResId, boolean isOnline) {
        this.id = id;
        this.name = name;
        this.initials = initials;
        this.gradientResId = gradientResId;
        this.isOnline = isOnline;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInitials() {
        return initials;
    }

    public int getGradientResId() {
        return gradientResId;
    }

    public boolean isOnline() {
        return isOnline;
    }
}
