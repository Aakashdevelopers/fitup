package com.amstudio.fitup;

public class ChatItem {
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_PHOTO_SNAP = 1;
    public static final int TYPE_VIDEO_SNAP = 2;

    private final String id;
    private final String name;
    private final String initials;
    private final int gradientResId;
    private final String lastMessage;
    private final String time;
    private final int unreadCount;
    private final boolean hasStreak;
    private final int streakCount;
    private final boolean isOnline;
    private final boolean hasRing;
    private final int lastMessageType;

    public ChatItem(String id, String name, String initials, int gradientResId,
                    String lastMessage, String time, int unreadCount,
                    boolean hasStreak, int streakCount, boolean isOnline, boolean hasRing) {
        this(id, name, initials, gradientResId, lastMessage, time, unreadCount,
                hasStreak, streakCount, isOnline, hasRing, TYPE_TEXT);
    }

    public ChatItem(String id, String name, String initials, int gradientResId,
                    String lastMessage, String time, int unreadCount,
                    boolean hasStreak, int streakCount, boolean isOnline, boolean hasRing,
                    int lastMessageType) {
        this.id = id;
        this.name = name;
        this.initials = initials;
        this.gradientResId = gradientResId;
        this.lastMessage = lastMessage;
        this.time = time;
        this.unreadCount = unreadCount;
        this.hasStreak = hasStreak;
        this.streakCount = streakCount;
        this.isOnline = isOnline;
        this.hasRing = hasRing;
        this.lastMessageType = lastMessageType;
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

    public String getLastMessage() {
        return lastMessage;
    }

    public String getTime() {
        return time;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public boolean hasStreak() {
        return hasStreak;
    }

    public int getStreakCount() {
        return streakCount;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public boolean hasRing() {
        return hasRing;
    }

    public int getLastMessageType() {
        return lastMessageType;
    }

    public boolean isSnapMessage() {
        return lastMessageType == TYPE_PHOTO_SNAP || lastMessageType == TYPE_VIDEO_SNAP;
    }
}
