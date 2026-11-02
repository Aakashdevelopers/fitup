package com.amstudio.fitbond.repository;

import android.app.Application;
import android.util.Log;
import android.view.View;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoAudioRoute;
import im.zego.zegoexpress.constants.ZegoPlayerState;
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoRemoteDeviceState;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.constants.ZegoViewMode;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoRoomConfig;
import im.zego.zegoexpress.entity.ZegoStream;
import im.zego.zegoexpress.entity.ZegoUser;

import org.json.JSONObject;

import java.util.ArrayList;

public class ZegoCallManager {

    private static final String TAG = "ZegoCallManager";

    public static final long APP_ID = 324300005L;
    public static final String APP_SIGN = "a21989d2080cd23e895091d978192e5281b26f11fcba2fa47d284df9bd047c4e";

    private static ZegoCallManager instance;
    private ZegoExpressEngine engine;
    private String currentRoomId;
    private View localVideoView;
    private View remoteVideoView;
    private boolean isEngineInitialized = false;

    public interface CallEventListener {
        void onRemoteUserJoined(String streamId);
        void onRemoteUserLeft(String streamId);
        void onRemoteCameraStateChanged(String streamId, boolean isOpen);
        void onError(int errorCode, String message);
    }

    public interface SdkConnectionListener {
        void onSdkConnected();
        void onSdkDisconnected();
    }

    private CallEventListener eventListener;
    private SdkConnectionListener connectionListener;

    private ZegoCallManager() {}

    public static synchronized ZegoCallManager getInstance() {
        if (instance == null) {
            instance = new ZegoCallManager();
        }
        return instance;
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }

    public void setEventListener(CallEventListener listener) {
        this.eventListener = listener;
    }

    public void setSdkConnectionListener(SdkConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void initSDK(Application application) {
        if (isEngineInitialized && engine != null) return;

        ZegoEngineProfile profile = new ZegoEngineProfile();
        profile.appID = APP_ID;
        profile.appSign = APP_SIGN;
        profile.scenario = ZegoScenario.DEFAULT;
        profile.application = application;

        engine = ZegoExpressEngine.createEngine(profile, new IZegoEventHandler() {
            @Override
            public void onRoomStreamUpdate(String roomID, ZegoUpdateType updateType,
                                           ArrayList<ZegoStream> streamList, JSONObject extendedData) {
                Log.d(TAG, "onRoomStreamUpdate: " + updateType + ", count: " + (streamList != null ? streamList.size() : 0));
                if (streamList == null) return;

                if (updateType == ZegoUpdateType.ADD) {
                    for (ZegoStream stream : streamList) {
                        Log.d(TAG, "Remote stream added: " + stream.streamID);
                        startPlayingRemoteStream(stream.streamID);
                        if (eventListener != null) {
                            eventListener.onRemoteUserJoined(stream.streamID);
                        }
                    }
                    if (connectionListener != null) {
                        connectionListener.onSdkConnected();
                    }
                } else if (updateType == ZegoUpdateType.DELETE) {
                    for (ZegoStream stream : streamList) {
                        Log.d(TAG, "Remote stream removed: " + stream.streamID);
                        stopPlayingRemoteStream(stream.streamID);
                        if (eventListener != null) {
                            eventListener.onRemoteUserLeft(stream.streamID);
                        }
                    }
                }
            }

            @Override
            public void onPublisherStateUpdate(String streamID, ZegoPublisherState state, int errorCode, JSONObject extendedData) {
                Log.d(TAG, "onPublisherStateUpdate: " + streamID + ", state: " + state + ", error: " + errorCode);
                // Note: Local publisher stream state change (e.g. caller starting outgoing audio/video)
                // must NOT trigger onSdkConnected because the remote receiver has not answered yet.
            }

            @Override
            public void onPlayerStateUpdate(String streamID, ZegoPlayerState state, int errorCode, JSONObject extendedData) {
                Log.d(TAG, "onPlayerStateUpdate: " + streamID + ", state: " + state + ", error: " + errorCode);
                if (state == ZegoPlayerState.PLAYING && errorCode == 0) {
                    Log.d(TAG, "Zego SDK player playing confirmed - Remote media stream active");
                    if (connectionListener != null) {
                        connectionListener.onSdkConnected();
                    }
                }
            }

            @Override
            public void onAudioRouteChange(ZegoAudioRoute audioRoute) {
                Log.d(TAG, "onAudioRouteChange: " + audioRoute);
            }

            @Override
            public void onRemoteCameraStateUpdate(String streamID, ZegoRemoteDeviceState state) {
                Log.d(TAG, "onRemoteCameraStateUpdate: " + streamID + ", state: " + state);
                if (eventListener != null) {
                    boolean isOpen = (state == ZegoRemoteDeviceState.OPEN);
                    eventListener.onRemoteCameraStateChanged(streamID, isOpen);
                }
            }
        });

        if (engine != null) {
            engine.enableAudioCaptureDevice(true);
            engine.muteMicrophone(false);
            engine.mutePublishStreamAudio(false);
            engine.muteAllPlayStreamAudio(false);
            engine.setCaptureVolume(100);
        }

        isEngineInitialized = (engine != null);
        Log.d(TAG, "ZEGO Express Engine initialized: " + isEngineInitialized);
    }

    public void joinRoomAndStartCall(Application application, String roomId, String userId, String userName,
                                     boolean isVideo, View localView, View remoteView) {
        if (engine == null) {
            initSDK(application);
        }

        if (engine == null) {
            Log.e(TAG, "Failed to initialize ZEGO Express Engine");
            return;
        }

        this.currentRoomId = roomId;
        this.localVideoView = localView;
        this.remoteVideoView = remoteView;

        ZegoUser user = new ZegoUser(userId != null ? userId : "user_" + System.currentTimeMillis(),
                userName != null ? userName : "User");

        ZegoRoomConfig roomConfig = new ZegoRoomConfig();
        roomConfig.isUserStatusNotify = true;

        engine.loginRoom(roomId, user, roomConfig, (errorCode, extendedData) -> {
            if (errorCode == 0) {
                Log.d(TAG, "Successfully logged in to ZEGO room: " + roomId);
                startPublishingLocalStream(roomId, userId, isVideo);
            } else {
                Log.e(TAG, "Failed to login ZEGO room, error: " + errorCode);
                if (eventListener != null) {
                    eventListener.onError(errorCode, "Room login error: " + errorCode);
                }
            }
        });
    }

    private void startPublishingLocalStream(String roomId, String userId, boolean isVideo) {
        if (engine == null) return;

        String streamId = roomId + "_" + (userId != null ? userId : "stream");

        engine.enableAudioCaptureDevice(true);
        engine.muteMicrophone(false);
        engine.mutePublishStreamAudio(false);
        engine.setCaptureVolume(100);
        engine.setAudioRouteToSpeaker(true);

        if (isVideo && localVideoView != null) {
            engine.enableCamera(true);
            ZegoCanvas localCanvas = new ZegoCanvas(localVideoView);
            localCanvas.viewMode = ZegoViewMode.ASPECT_FILL;
            engine.startPreview(localCanvas);
        } else {
            engine.enableCamera(false);
        }

        engine.startPublishingStream(streamId);
        Log.d(TAG, "Started publishing stream: " + streamId + " (isVideo=" + isVideo + ")");
    }

    private String currentRemoteStreamId;

    public void setRemoteVideoView(View remoteView) {
        this.remoteVideoView = remoteView;
        if (engine != null && remoteView != null && currentRemoteStreamId != null) {
            ZegoCanvas remoteCanvas = new ZegoCanvas(remoteView);
            remoteCanvas.viewMode = ZegoViewMode.ASPECT_FILL;
            engine.startPlayingStream(currentRemoteStreamId, remoteCanvas);
        }
    }

    public void setLocalVideoView(View localView) {
        this.localVideoView = localView;
        if (engine != null && localView != null) {
            ZegoCanvas localCanvas = new ZegoCanvas(localView);
            localCanvas.viewMode = ZegoViewMode.ASPECT_FILL;
            engine.startPreview(localCanvas);
        }
    }

    private void startPlayingRemoteStream(String streamId) {
        if (engine == null) return;
        this.currentRemoteStreamId = streamId;

        engine.mutePlayStreamAudio(streamId, false);
        engine.mutePlayStreamVideo(streamId, false);
        engine.setPlayVolume(streamId, 100);

        if (remoteVideoView != null && remoteVideoView.getVisibility() == View.VISIBLE) {
            ZegoCanvas remoteCanvas = new ZegoCanvas(remoteVideoView);
            remoteCanvas.viewMode = ZegoViewMode.ASPECT_FILL;
            engine.startPlayingStream(streamId, remoteCanvas);
        } else {
            engine.startPlayingStream(streamId);
        }
        Log.d(TAG, "Started playing remote stream: " + streamId);
    }

    private void stopPlayingRemoteStream(String streamId) {
        if (engine != null) {
            engine.stopPlayingStream(streamId);
            Log.d(TAG, "Stopped playing remote stream: " + streamId);
        }
        if (streamId != null && streamId.equals(currentRemoteStreamId)) {
            this.currentRemoteStreamId = null;
        }
    }

    public void setMute(boolean isMuted) {
        if (engine != null) {
            engine.muteMicrophone(isMuted);
            engine.mutePublishStreamAudio(isMuted);
        }
    }

    public void setSpeaker(boolean isSpeakerOn) {
        if (engine != null) {
            engine.setAudioRouteToSpeaker(isSpeakerOn);
            if (currentRemoteStreamId != null) {
                engine.setPlayVolume(currentRemoteStreamId, isSpeakerOn ? 100 : 70);
            }
        }
    }

    public void enableCamera(boolean enable) {
        if (engine != null) {
            engine.enableCamera(enable);
            engine.mutePublishStreamVideo(!enable);
        }
    }

    public void switchCamera(boolean useFront) {
        if (engine != null) {
            engine.useFrontCamera(useFront);
        }
    }

    public void leaveRoomAndEndCall() {
        if (engine != null) {
            try {
                engine.stopPreview();
                engine.stopPublishingStream();
                if (currentRoomId != null) {
                    engine.logoutRoom(currentRoomId);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error leaving ZEGO room: " + e.getMessage());
            }
        }
        if (connectionListener != null) {
            connectionListener.onSdkDisconnected();
        }
        this.currentRoomId = null;
        this.localVideoView = null;
        this.remoteVideoView = null;
        this.currentRemoteStreamId = null;
        this.eventListener = null;
        this.connectionListener = null;
    }

    public void destroyEngine() {
        leaveRoomAndEndCall();
        if (engine != null) {
            try {
                ZegoExpressEngine.destroyEngine(null);
            } catch (Exception e) {
                Log.w(TAG, "Error destroying ZEGO Express Engine: " + e.getMessage());
            }
            engine = null;
            isEngineInitialized = false;
        }
    }
}
