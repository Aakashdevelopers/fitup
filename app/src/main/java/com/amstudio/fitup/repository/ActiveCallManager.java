package com.amstudio.fitup.repository;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveCallManager {

    private static ActiveCallManager instance;

    private String activeCallId;
    private String targetUid;
    private String userName;
    private String userInitials;
    private boolean isVideo;
    private long callStartTimeMs = 0;
    private boolean isActive = false;

    private boolean isSpeakerOn = true;
    private boolean isMuteOn = false;
    private boolean isCameraOn = true;

    private ListenerRegistration activeCallFirestoreListener;

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

        this.isSpeakerOn = isVideo;
        this.isMuteOn = false;
        this.isCameraOn = isVideo;

        attachFirestoreListener(callId);
        notifyListeners();
    }

    private void attachFirestoreListener(String callId) {
        detachFirestoreListener();
        if (callId == null || callId.isEmpty()) return;

        activeCallFirestoreListener = FirebaseFirestore.getInstance()
                .collection("calls")
                .document(callId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;
                    String status = doc.getString("status");
                    if ("ENDED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                        ZegoCallManager.getInstance().leaveRoomAndEndCall();
                        clearActiveCall();
                    }
                });
    }

    private void detachFirestoreListener() {
        if (activeCallFirestoreListener != null) {
            activeCallFirestoreListener.remove();
            activeCallFirestoreListener = null;
        }
    }

    public void clearActiveCall() {
        detachFirestoreListener();
        this.isActive = false;
        this.activeCallId = null;
        this.targetUid = null;
        notifyListeners();
    }

    public void toggleSpeaker() {
        this.isSpeakerOn = !this.isSpeakerOn;
        ZegoCallManager.getInstance().setSpeaker(isSpeakerOn);
        notifyListeners();
    }

    public void toggleMute() {
        this.isMuteOn = !this.isMuteOn;
        ZegoCallManager.getInstance().setMute(isMuteOn);
        notifyListeners();
    }

    public void toggleCamera() {
        this.isCameraOn = !this.isCameraOn;
        ZegoCallManager.getInstance().enableCamera(isCameraOn);
        notifyListeners();
    }

    public void endCall() {
        if (activeCallId != null && !activeCallId.isEmpty()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "ENDED");
            updates.put("endedAt", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("calls").document(activeCallId).update(updates);
        }
        ZegoCallManager.getInstance().leaveRoomAndEndCall();
        clearActiveCall();
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

    public boolean isSpeakerOn() {
        return isSpeakerOn;
    }

    public boolean isMuteOn() {
        return isMuteOn;
    }

    public boolean isCameraOn() {
        return isCameraOn;
    }
}
