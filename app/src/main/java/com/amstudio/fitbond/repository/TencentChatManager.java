package com.amstudio.fitbond.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.amstudio.fitbond.helper.OneSignalManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener;
import com.tencent.imsdk.v2.V2TIMCallback;
import com.tencent.imsdk.v2.V2TIMConversation;
import com.tencent.imsdk.v2.V2TIMConversationResult;
import com.tencent.imsdk.v2.V2TIMManager;
import com.tencent.imsdk.v2.V2TIMMessage;
import com.tencent.imsdk.v2.V2TIMSDKConfig;
import com.tencent.imsdk.v2.V2TIMSendCallback;
import com.tencent.imsdk.v2.V2TIMValueCallback;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TencentChatManager {

    private static final String TAG = "TencentChatManager";
    private static TencentChatManager instance;

    private boolean isInitialized = false;
    private boolean isLoggedIn = false;
    private final FirebaseFunctions functions;

    public interface AuthCallback {
        void onSuccess(String userId);
        void onError(Exception e);
    }

    public interface CredentialsCallback {
        void onSuccess(long sdkAppId, String userId, String userSig);
        void onError(Exception e);
    }

    public interface ConversationListCallback {
        void onSuccess(List<V2TIMConversation> conversations);
        void onError(int code, String desc);
    }

    public interface MessageListCallback {
        void onSuccess(List<V2TIMMessage> messages);
        void onError(int code, String desc);
    }

    public interface MessageCallback {
        void onSuccess(V2TIMMessage message);
        void onError(int code, String desc);
    }

    private TencentChatManager() {
        this.functions = FirebaseFunctions.getInstance();
    }

    public static synchronized TencentChatManager getInstance() {
        if (instance == null) {
            instance = new TencentChatManager();
        }
        return instance;
    }

    public void initSDK(Context context, long sdkAppId) {
        if (isInitialized) return;

        V2TIMSDKConfig config = new V2TIMSDKConfig();
        config.setLogLevel(V2TIMSDKConfig.V2TIM_LOG_WARN);

        boolean initSuccess = V2TIMManager.getInstance().initSDK(
                context.getApplicationContext(),
                (int) sdkAppId,
                config
        );

        if (initSuccess) {
            isInitialized = true;
            Log.d(TAG, "Tencent IM SDK initialized successfully.");
        } else {
            Log.e(TAG, "Failed to initialize Tencent IM SDK.");
        }
    }

    public void loginWithFirebaseUser(AuthCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Firebase user is not authenticated."));
            return;
        }

        String userId = currentUser.getUid();

        if (isLoggedIn) {
            callback.onSuccess(userId);
            return;
        }

        Context context = ContextHolder.getContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("tencent_sig_cache", Context.MODE_PRIVATE);
            String cachedUser = prefs.getString("cached_uid", "");
            String cachedSig = prefs.getString("cached_usersig", "");
            long cachedAppId = prefs.getLong("cached_sdkappid", 0);
            long cachedTime = prefs.getLong("cached_time", 0);
            long fiveDaysInMillis = 5L * 24 * 3600 * 1000;

            if (userId.equals(cachedUser) && !cachedSig.isEmpty() && cachedAppId > 0 && (System.currentTimeMillis() - cachedTime < fiveDaysInMillis)) {
                Log.d(TAG, "Using valid cached Tencent UserSig for user: " + userId);
                if (!isInitialized) {
                    initSDK(context, cachedAppId);
                }

                V2TIMManager.getInstance().login(userId, cachedSig, new V2TIMCallback() {
                    @Override
                    public void onSuccess() {
                        isLoggedIn = true;
                        Log.d(TAG, "Tencent IM login success (via cached UserSig) for user: " + userId);
                        setupGlobalDeliveryListener();
                        callback.onSuccess(userId);
                    }

                    @Override
                    public void onError(int code, String desc) {
                        Log.w(TAG, "Cached UserSig login failed (" + code + "), refreshing from server...");
                        prefs.edit().clear().apply();
                        fetchUserSigAndLogin(userId, callback);
                    }
                });
                return;
            }
        }

        fetchUserSigAndLogin(userId, callback);
    }

    private void fetchUserSigAndLogin(String userId, AuthCallback callback) {
        functions.getHttpsCallable("generateTencentUserSig")
                .call()
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> data = (Map<String, Object>) result.getData();
                        if (data != null && data.containsKey("userSig") && data.containsKey("sdkAppId")) {
                            String userSig = (String) data.get("userSig");
                            long sdkAppId = ((Number) data.get("sdkAppId")).longValue();

                            Context context = ContextHolder.getContext();
                            if (context != null) {
                                SharedPreferences prefs = context.getSharedPreferences("tencent_sig_cache", Context.MODE_PRIVATE);
                                prefs.edit()
                                        .putString("cached_uid", userId)
                                        .putString("cached_usersig", userSig)
                                        .putLong("cached_sdkappid", sdkAppId)
                                        .putLong("cached_time", System.currentTimeMillis())
                                        .apply();

                                if (!isInitialized) {
                                    initSDK(context, sdkAppId);
                                }
                            }

                            V2TIMManager.getInstance().login(userId, userSig, new V2TIMCallback() {
                                @Override
                                public void onSuccess() {
                                    isLoggedIn = true;
                                    Log.d(TAG, "Tencent IM login success for user: " + userId);
                                    setupGlobalDeliveryListener();
                                    callback.onSuccess(userId);
                                }

                                @Override
                                public void onError(int code, String desc) {
                                    Log.e(TAG, "Tencent IM login failed. Code: " + code + ", Desc: " + desc);
                                    callback.onError(new Exception("Tencent Login Error (" + code + "): " + desc));
                                }
                            });

                        } else {
                            callback.onError(new Exception("Invalid UserSig response from server."));
                        }
                    } catch (Exception e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void getTrtcCredentials(CredentialsCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Firebase user is not authenticated."));
            return;
        }

        String userId = currentUser.getUid();
        Context context = ContextHolder.getContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("tencent_sig_cache", Context.MODE_PRIVATE);
            String cachedUser = prefs.getString("cached_uid", "");
            String cachedSig = prefs.getString("cached_usersig", "");
            long cachedAppId = prefs.getLong("cached_sdkappid", 0);
            long cachedTime = prefs.getLong("cached_time", 0);
            long fiveDaysInMillis = 5L * 24 * 3600 * 1000;

            if (userId.equals(cachedUser) && !cachedSig.isEmpty() && cachedAppId > 0 && (System.currentTimeMillis() - cachedTime < fiveDaysInMillis)) {
                callback.onSuccess(cachedAppId, userId, cachedSig);
                return;
            }
        }

        fetchUserSigAndLogin(userId, new AuthCallback() {
            @Override
            public void onSuccess(String uId) {
                if (context != null) {
                    SharedPreferences prefs = context.getSharedPreferences("tencent_sig_cache", Context.MODE_PRIVATE);
                    String sig = prefs.getString("cached_usersig", "");
                    long appId = prefs.getLong("cached_sdkappid", 0);
                    if (!sig.isEmpty() && appId > 0) {
                        callback.onSuccess(appId, uId, sig);
                        return;
                    }
                }
                callback.onError(new Exception("Failed to load TRTC credentials."));
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getConversationList(ConversationListCallback callback) {
        V2TIMManager.getConversationManager().getConversationList(0, 50, new V2TIMValueCallback<V2TIMConversationResult>() {
            @Override
            public void onSuccess(V2TIMConversationResult result) {
                if (result != null) {
                    callback.onSuccess(result.getConversationList());
                } else {
                    callback.onError(-1, "Null conversation result");
                }
            }

            @Override
            public void onError(int code, String desc) {
                callback.onError(code, desc);
            }
        });
    }

    public void getC2CHistoryMessageList(String userId, int count, V2TIMMessage lastMsg, MessageListCallback callback) {
        V2TIMManager.getMessageManager().getC2CHistoryMessageList(userId, count, lastMsg, new V2TIMValueCallback<List<V2TIMMessage>>() {
            @Override
            public void onSuccess(List<V2TIMMessage> v2TIMMessages) {
                if (v2TIMMessages != null) {
                    callback.onSuccess(v2TIMMessages);
                } else {
                    callback.onSuccess(new ArrayList<>());
                }
            }

            @Override
            public void onError(int code, String desc) {
                callback.onError(code, desc);
            }
        });
    }

    public void sendTextMessage(String receiverUserId, String text, MessageCallback callback) {
        // Send OneSignal Push Notification immediately off the main thread
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String senderName = currentUser != null && currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "FitBond User";
        String senderUid = currentUser != null ? currentUser.getUid() : "";
        OneSignalManager.getInstance().sendChatNotification(receiverUserId, senderName, text, senderUid);

        // Send live Tencent IM message
        V2TIMMessage message = V2TIMManager.getMessageManager().createTextMessage(text);
        V2TIMManager.getMessageManager().sendMessage(
                message,
                receiverUserId,
                null,
                V2TIMMessage.V2TIM_PRIORITY_NORMAL,
                false,
                null,
                new V2TIMSendCallback<V2TIMMessage>() {
                    @Override
                    public void onProgress(int progress) {}

                    @Override
                    public void onSuccess(V2TIMMessage v2TIMMessage) {
                        callback.onSuccess(v2TIMMessage);
                    }

                    @Override
                    public void onError(int code, String desc) {
                        callback.onError(code, desc);
                    }
                }
        );
    }

    public void sendImageMessage(String receiverUserId, String imagePath, MessageCallback callback) {
        // Send OneSignal Push Notification immediately off the main thread
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String senderName = currentUser != null && currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "FitBond User";
        String senderUid = currentUser != null ? currentUser.getUid() : "";
        OneSignalManager.getInstance().sendChatNotification(receiverUserId, senderName, "📷 Sent a photo", senderUid);

        // Send live Tencent IM message
        V2TIMMessage message = V2TIMManager.getMessageManager().createImageMessage(imagePath);
        V2TIMManager.getMessageManager().sendMessage(
                message,
                receiverUserId,
                null,
                V2TIMMessage.V2TIM_PRIORITY_NORMAL,
                false,
                null,
                new V2TIMSendCallback<V2TIMMessage>() {
                    @Override
                    public void onProgress(int progress) {}

                    @Override
                    public void onSuccess(V2TIMMessage v2TIMMessage) {
                        callback.onSuccess(v2TIMMessage);
                    }

                    @Override
                    public void onError(int code, String desc) {
                        callback.onError(code, desc);
                    }
                }
        );
    }

    public void sendCustomSnapMessage(String receiverUserId, String snapId, String mediaType,
                                     String mediaUrl, long duration, MessageCallback callback) {
        try {
            JSONObject customJson = new JSONObject();
            customJson.put("type", "SNAP");
            customJson.put("snapId", snapId);
            customJson.put("mediaType", mediaType);
            customJson.put("mediaUrl", mediaUrl);
            customJson.put("duration", duration);

            byte[] dataBytes = customJson.toString().getBytes(StandardCharsets.UTF_8);
            V2TIMMessage customMessage = V2TIMManager.getMessageManager().createCustomMessage(dataBytes);

            V2TIMManager.getMessageManager().sendMessage(
                    customMessage,
                    receiverUserId,
                    null,
                    V2TIMMessage.V2TIM_PRIORITY_HIGH,
                    false,
                    null,
                    new V2TIMSendCallback<V2TIMMessage>() {
                        @Override
                        public void onProgress(int progress) {}

                        @Override
                        public void onSuccess(V2TIMMessage v2TIMMessage) {
                            callback.onSuccess(v2TIMMessage);
                        }

                        @Override
                        public void onError(int code, String desc) {
                            callback.onError(code, desc);
                        }
                    }
            );

        } catch (Exception e) {
            callback.onError(-1, "JSON build error: " + e.getMessage());
        }
    }

    public void sendTrtcCallSignalMessage(String receiverUserId, String callId, String callType,
                                          String action, MessageCallback callback) {
        try {
            // Trigger OneSignal Push Notification for call events
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String senderName = currentUser != null && currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "FitBond User";
            String senderUid = currentUser != null ? currentUser.getUid() : "";
            String senderAvatar = currentUser != null && currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "";
            boolean isVideo = "VIDEO".equalsIgnoreCase(callType);

            if ("CALL_START".equalsIgnoreCase(action) || "INVITE".equalsIgnoreCase(action)) {
                OneSignalManager.getInstance().sendCallNotification(receiverUserId, senderName, senderUid, senderAvatar, callId, isVideo, "INVITE");
            } else if ("CANCEL".equalsIgnoreCase(action) || "CANCELLED".equalsIgnoreCase(action)) {
                OneSignalManager.getInstance().sendCallNotification(receiverUserId, senderName, senderUid, senderAvatar, callId, isVideo, "CANCEL");
            } else if ("REJECT".equalsIgnoreCase(action) || "REJECTED".equalsIgnoreCase(action)) {
                OneSignalManager.getInstance().sendMissedCallNotification(receiverUserId, senderName, senderUid, senderAvatar, callId, isVideo);
            } else if ("END".equalsIgnoreCase(action) || "ENDED".equalsIgnoreCase(action)) {
                OneSignalManager.getInstance().sendCallNotification(receiverUserId, senderName, senderUid, senderAvatar, callId, isVideo, "END");
            }

            JSONObject customJson = new JSONObject();
            customJson.put("type", "TRTC_CALL_SIGNAL");
            customJson.put("callId", callId);
            customJson.put("callType", callType);
            customJson.put("action", action);

            byte[] dataBytes = customJson.toString().getBytes(StandardCharsets.UTF_8);
            V2TIMMessage customMessage = V2TIMManager.getMessageManager().createCustomMessage(dataBytes);

            V2TIMManager.getMessageManager().sendMessage(
                    customMessage,
                    receiverUserId,
                    null,
                    V2TIMMessage.V2TIM_PRIORITY_HIGH,
                    false,
                    null,
                    new V2TIMSendCallback<V2TIMMessage>() {
                        @Override
                        public void onProgress(int progress) {}

                        @Override
                        public void onSuccess(V2TIMMessage v2TIMMessage) {
                            if (callback != null) callback.onSuccess(v2TIMMessage);
                        }

                        @Override
                        public void onError(int code, String desc) {
                            if (callback != null) callback.onError(code, desc);
                        }
                    }
            );

        } catch (Exception e) {
            if (callback != null) callback.onError(-1, "JSON build error: " + e.getMessage());
        }
    }

    public void addAdvancedMsgListener(V2TIMAdvancedMsgListener listener) {
        V2TIMManager.getMessageManager().addAdvancedMsgListener(listener);
    }

    public void removeAdvancedMsgListener(V2TIMAdvancedMsgListener listener) {
        V2TIMManager.getMessageManager().removeAdvancedMsgListener(listener);
    }

    public void setupGlobalDeliveryListener() {
        addAdvancedMsgListener(new V2TIMAdvancedMsgListener() {
            @Override
            public void onRecvNewMessage(V2TIMMessage msg) {
                if (msg == null || msg.isSelf()) return;
                String sender = msg.getSender() != null ? msg.getSender() : msg.getUserID();
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (sender != null && currentUser != null) {
                    String currentUid = currentUser.getUid();
                    String conversationId = currentUid.compareTo(sender) < 0 ? currentUid + "_" + sender : sender + "_" + currentUid;
                    String msgId = msg.getMsgID();
                    if (msgId != null && !msgId.isEmpty()) {
                        DocumentReference ref = FirebaseFirestore.getInstance()
                                .collection("chats").document(conversationId)
                                .collection("messages").document(msgId);
                        ref.get().addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String status = doc.getString("status");
                                if (status == null || "SENT".equalsIgnoreCase(status)) {
                                    ref.update("status", "DELIVERED");
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    public void logout(V2TIMCallback callback) {
        V2TIMManager.getInstance().logout(new V2TIMCallback() {
            @Override
            public void onSuccess() {
                isLoggedIn = false;
                if (callback != null) callback.onSuccess();
            }

            @Override
            public void onError(int code, String desc) {
                if (callback != null) callback.onError(code, desc);
            }
        });
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    // Context holder helper for SDK init
    public static class ContextHolder {
        private static Context appContext;
        public static void setContext(Context context) {
            if (context != null) {
                appContext = context.getApplicationContext();
                if (!getInstance().isInitialized) {
                    getInstance().initSDK(appContext, 20050167L);
                }
            }
        }
        public static Context getContext() {
            return appContext;
        }
    }
}
