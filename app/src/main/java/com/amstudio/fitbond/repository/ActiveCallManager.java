package com.amstudio.fitbond.repository;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveCallManager {

    private static final String TAG = "ActiveCallManager";

    public enum CallState {
        IDLE,
        OUTGOING,
        RINGING,
        ACCEPTED,
        CONNECTING,
        CONNECTED,
        REJECTED,
        CANCELLED,
        ENDED,
        MISSED,
        FAILED,
        CALL_INITIATED,
        CALL_RINGING,
        CALL_ACCEPTED,
        CALL_REJECTED,
        CALL_CANCELLED,
        CALL_ENDED,
        CALL_MISSED,
        CALL_FAILED
    }

    private static ActiveCallManager instance;

    private String activeCallId;
    private String targetUid;
    private String userName;
    private String userInitials;
    private String avatarUrl;
    private boolean isVideo;
    private long callStartTimeMs = 0;
    private long connectedAtMs = 0;
    private boolean isActive = false;
    private CallState callState = CallState.IDLE;

    private boolean isSpeakerOn = true;
    private boolean isMuteOn = false;
    private boolean isCameraOn = true;

    private ListenerRegistration activeCallFirestoreListener;

    public interface OnCallStateChangeListener {
        void onCallStateChanged(boolean isActive, String callId, String userName, String userInitials, boolean isVideo, long callStartTimeMs);
    }

    public interface OnDetailedCallStateChangeListener {
        void onDetailedCallStateChanged(CallState state, String callId, String targetUid, String userName, boolean isVideo);
    }

    private final List<OnCallStateChangeListener> listeners = new ArrayList<>();
    private final List<OnDetailedCallStateChangeListener> detailedListeners = new ArrayList<>();

    private ActiveCallManager() {}

    public static synchronized ActiveCallManager getInstance() {
        if (instance == null) {
            instance = new ActiveCallManager();
        }
        return instance;
    }

    public synchronized void setCallState(CallState newState) {
        Log.d(TAG, "Call state transition: " + this.callState + " -> " + newState + " (callId=" + activeCallId + ")");
        this.callState = newState;
        if (newState == CallState.IDLE || newState == CallState.ENDED || newState == CallState.CALL_ENDED ||
            newState == CallState.REJECTED || newState == CallState.CALL_REJECTED ||
            newState == CallState.CANCELLED || newState == CallState.CALL_CANCELLED ||
            newState == CallState.MISSED || newState == CallState.CALL_MISSED ||
            newState == CallState.FAILED || newState == CallState.CALL_FAILED) {
            this.isActive = false;
            this.connectedAtMs = 0;
        } else {
            this.isActive = true;
        }
        notifyDetailedListeners();
    }

    public synchronized CallState getCallState() {
        return callState;
    }

    public synchronized long getConnectedAtMs() {
        return connectedAtMs;
    }

    public synchronized void setConnectedAtMs(long timestampMs) {
        this.connectedAtMs = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
    }

    public synchronized void setCallConnected(long timestampMs) {
        if (this.connectedAtMs <= 0) {
            this.connectedAtMs = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
        }
        setCallState(CallState.CONNECTED);
        notifyListeners();
    }

    public synchronized void setActiveCall(String callId, String targetUid, String userName, String userInitials, boolean isVideo, long callStartTimeMs) {
        setActiveCall(callId, targetUid, userName, userInitials, null, isVideo, callStartTimeMs, CallState.RINGING);
    }

    public synchronized void setActiveCall(String callId, String targetUid, String userName, String userInitials,
                                            String avatarUrl, boolean isVideo, long callStartTimeMs, CallState initialMode) {
        this.activeCallId = callId;
        this.targetUid = targetUid;
        this.userName = userName;
        this.userInitials = userInitials;
        this.avatarUrl = avatarUrl;
        this.isVideo = isVideo;
        this.callStartTimeMs = callStartTimeMs > 0 ? callStartTimeMs : System.currentTimeMillis();
        this.isActive = true;
        this.callState = initialMode != null ? initialMode : CallState.RINGING;

        if (this.callState == CallState.CONNECTED) {
            if (this.connectedAtMs <= 0) {
                this.connectedAtMs = this.callStartTimeMs;
            }
        } else {
            this.connectedAtMs = 0;
        }

        this.isSpeakerOn = isVideo;
        this.isMuteOn = false;
        this.isCameraOn = isVideo;

        attachFirestoreListener(callId);
        notifyListeners();
        notifyDetailedListeners();
    }

    public synchronized boolean canAcceptIncomingCall(String incomingCallId) {
        if (incomingCallId == null || incomingCallId.isEmpty()) return false;
        if (this.isActive && activeCallId != null && !activeCallId.equals(incomingCallId)) {
            Log.w(TAG, "Rejecting incoming call " + incomingCallId + ": Active call already in progress (" + activeCallId + ")");
            return false;
        }
        if (callState == CallState.ENDED || callState == CallState.CALL_ENDED ||
            callState == CallState.CANCELLED || callState == CallState.CALL_CANCELLED ||
            callState == CallState.REJECTED || callState == CallState.CALL_REJECTED) {
            return true;
        }
        return !isActive || callState == CallState.IDLE;
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
                    if ("ENDED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
                        ZegoCallManager.getInstance().leaveRoomAndEndCall();
                        if ("ENDED".equalsIgnoreCase(status)) setCallState(CallState.ENDED);
                        else if ("REJECTED".equalsIgnoreCase(status)) setCallState(CallState.REJECTED);
                        else if ("CANCELLED".equalsIgnoreCase(status)) setCallState(CallState.CANCELLED);
                        else setCallState(CallState.IDLE);
                        clearActiveCall();
                    } else if ("CONNECTED".equalsIgnoreCase(status)) {
                        Timestamp connTs = doc.getTimestamp("connectedAt");
                        long ts = (connTs != null) ? connTs.toDate().getTime() : System.currentTimeMillis();
                        setCallConnected(ts);
                    } else if ("ACCEPTED".equalsIgnoreCase(status)) {
                        if (callState != CallState.CONNECTED) {
                            setCallState(CallState.ACCEPTED);
                        }
                    }
                });
    }

    private void detachFirestoreListener() {
        if (activeCallFirestoreListener != null) {
            activeCallFirestoreListener.remove();
            activeCallFirestoreListener = null;
        }
    }

    public synchronized void clearActiveCall() {
        detachFirestoreListener();
        this.isActive = false;
        this.callState = CallState.IDLE;
        this.activeCallId = null;
        this.targetUid = null;
        this.connectedAtMs = 0;
        this.callStartTimeMs = 0;
        notifyListeners();
        notifyDetailedListeners();
    }

    public void toggleSpeaker() {
        this.isSpeakerOn = !this.isSpeakerOn;
        ZegoCallManager.getInstance().setSpeaker(isSpeakerOn);
        notifyListeners();
        notifyDetailedListeners();
    }

    public void toggleMute() {
        this.isMuteOn = !this.isMuteOn;
        ZegoCallManager.getInstance().setMute(isMuteOn);
        notifyListeners();
        notifyDetailedListeners();
    }

    public void toggleCamera() {
        this.isCameraOn = !this.isCameraOn;
        ZegoCallManager.getInstance().enableCamera(isCameraOn);
        notifyListeners();
        notifyDetailedListeners();
    }

    public void endCall() {
        if (activeCallId != null && !activeCallId.isEmpty()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "ENDED");
            updates.put("endedAt", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("calls").document(activeCallId).update(updates);
        }
        setCallState(CallState.ENDED);
        ZegoCallManager.getInstance().leaveRoomAndEndCall();
        clearActiveCall();
    }

    public void addListener(OnCallStateChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            listener.onCallStateChanged(isActive, activeCallId, userName, userInitials, isVideo, callStartTimeMs);
        }
    }

    public void removeListener(OnCallStateChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void addDetailedListener(OnDetailedCallStateChangeListener listener) {
        if (listener != null && !detailedListeners.contains(listener)) {
            detailedListeners.add(listener);
            listener.onDetailedCallStateChanged(callState, activeCallId, targetUid, userName, isVideo);
        }
    }

    public void removeDetailedListener(OnDetailedCallStateChangeListener listener) {
        if (listener != null) {
            detailedListeners.remove(listener);
        }
    }

    private void notifyListeners() {
        for (OnCallStateChangeListener listener : new ArrayList<>(listeners)) {
            listener.onCallStateChanged(isActive, activeCallId, userName, userInitials, isVideo, callStartTimeMs);
        }
    }

    private void notifyDetailedListeners() {
        for (OnDetailedCallStateChangeListener listener : new ArrayList<>(detailedListeners)) {
            listener.onDetailedCallStateChanged(callState, activeCallId, targetUid, userName, isVideo);
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

    public String getAvatarUrl() {
        return avatarUrl;
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
