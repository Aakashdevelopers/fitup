package com.amstudio.fitup.repository;

import java.util.ArrayList;
import java.util.List;

public class ActiveCallManager {

    private static ActiveCallManager instance;

    private String activeCallId;
    private String targetUid;
    private String userName;
    private String userInitials;
    private boolean isVideo;
    private long callStartTimeMs = 0;
    private boolean isActive = false;

    public interface OnCallStateChangeListener {
        void onCallStateChanged(boolean isActive, String callId, String userName, String userInitials, boolean isVideo, long callStartTimeMs);
    }

    private final List<OnCallStateChangeListener> listeners = new ArrayList<>();

    private ActiveCallManager() {}

    public static synchronized ActiveCallManager getInstance() {
        if (instance == null) {
            instance = new ActiveCallManager();
        }
        return instance;
    }

    public void setActiveCall(String callId, String targetUid, String userName, String userInitials, boolean isVideo, long callStartTimeMs) {
        this.activeCallId = callId;
        this.targetUid = targetUid;
        this.userName = userName;
        this.userInitials = userInitials;
        this.isVideo = isVideo;
        this.callStartTimeMs = callStartTimeMs > 0 ? callStartTimeMs : System.currentTimeMillis();
        this.isActive = true;
        notifyListeners();
    }

    public void clearActiveCall() {
        this.isActive = false;
        this.activeCallId = null;
        this.targetUid = null;
        notifyListeners();
    }

    public void addListener(OnCallStateChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            // Immediately notify listener of current state
            listener.onCallStateChanged(isActive, activeCallId, userName, userInitials, isVideo, callStartTimeMs);
        }
    }

    public void removeListener(OnCallStateChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        for (OnCallStateChangeListener listener : new ArrayList<>(listeners)) {
            listener.onCallStateChanged(isActive, activeCallId, userName, userInitials, isVideo, callStartTimeMs);
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public String getActiveCallId() {
        return activeCallId;
    }

    public String getTargetUid() {
        return targetUid;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserInitials() {
        return userInitials;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public long getCallStartTimeMs() {
        return callStartTimeMs;
    }
}
