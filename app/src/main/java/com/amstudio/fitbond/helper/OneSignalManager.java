package com.amstudio.fitbond.helper;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.amstudio.fitbond.BuildConfig;
import com.amstudio.fitbond.IndividualChatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centralized OneSignal Manager to handle SDK calls off the main thread.
 */
public class OneSignalManager {

    private static final String TAG = "OneSignalManager";
    public static final String DEFAULT_APP_ID = "aba0fd67-8105-47c4-ba26-74bec857b03b";
    public static final String REST_API_KEY = BuildConfig.ONESIGNAL_REST_API_KEY;

    private static OneSignalManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isInitialized = false;

    private OneSignalManager() {}

    public static synchronized OneSignalManager getInstance() {
        if (instance == null) {
            instance = new OneSignalManager();
        }
        return instance;
    }

    public void initialize(Context context, String appId) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            if (isInitialized) return;

            Log.d(TAG, "Initializing OneSignal with App ID: " + appId);
            OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);
            OneSignal.initWithContext(appContext, appId);

            // Foreground Listener -> Delegate to CustomNotificationServiceExtension to avoid duplicate or unverified notifications
            OneSignal.getNotifications().addForegroundLifecycleListener(event -> {
                if (event == null || event.getNotification() == null) return;
                JSONObject data = event.getNotification().getAdditionalData();
                if (data != null) {
                    String type = data.optString("type");
                    if ("INCOMING_CALL".equals(type) || "TRTC_CALL_SIGNAL".equals(type) || "CHAT_MESSAGE".equals(type) || "MISSED_CALL".equals(type)) {
                        Log.d(TAG, "Foreground notification received via OneSignal lifecycle listener (delegated to NotificationServiceExtension)");
                        event.preventDefault();
                    }
                }
            });

            // Click Listener -> Navigate to Chat Screen if applicable
            OneSignal.getNotifications().addClickListener(event -> {
                if (event == null || event.getNotification() == null) return;
                JSONObject data = event.getNotification().getAdditionalData();
                if (data != null) {
                    String senderUid = data.optString("senderUid");
                    if (senderUid.isEmpty()) {
                        senderUid = data.optString("callerUid");
                    }
                    String senderName = data.optString("senderName");
                    if (senderName.isEmpty()) {
                        senderName = data.optString("callerName");
                    }

                    if (!senderUid.isEmpty()) {
                        Intent chatIntent = new Intent(appContext, IndividualChatActivity.class);
                        chatIntent.putExtra(IndividualChatActivity.EXTRA_CHAT_ID, senderUid);
                        chatIntent.putExtra(IndividualChatActivity.EXTRA_CHAT_NAME, senderName);
                        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        appContext.startActivity(chatIntent);
                    }
                }
            });

            isInitialized = true;
        });
    }

    public void login(String externalUserId) {
        if (externalUserId == null || externalUserId.isEmpty()) return;

        executor.execute(() -> {
            Log.d(TAG, "Logging into OneSignal with User ID: " + externalUserId);
            OneSignal.login(externalUserId);
        });
    }

    public void logout() {
        executor.execute(() -> {
            Log.d(TAG, "Logging out from OneSignal");
            OneSignal.logout();
        });
    }

    public void addTag(String key, String value) {
        if (key == null || value == null) return;

        executor.execute(() -> {
            OneSignal.getUser().addTag(key, value);
        });
    }

    public void addEmail(String email) {
        if (email == null || email.isEmpty()) return;

        executor.execute(() -> {
            OneSignal.getUser().addEmail(email);
        });
    }

    public void sendCallNotification(String receiverUserId, String callerName, String callId, boolean isVideo) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String callerUid = currentUser != null ? currentUser.getUid() : "";
        String callerAvatar = currentUser != null && currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "";
        sendCallNotification(receiverUserId, callerName, callerUid, callerAvatar, callId, isVideo, "INVITE");
    }

    public void sendCallNotification(String receiverUserId, String callerName, String callerUid,
                                     String callerAvatar, String callId, boolean isVideo) {
        sendCallNotification(receiverUserId, callerName, callerUid, callerAvatar, callId, isVideo, "INVITE");
    }

    public void sendCallNotification(String receiverUserId, String callerName, String callerUid,
                                     String callerAvatar, String callId, boolean isVideo, String action) {
        if (receiverUserId == null || receiverUserId.isEmpty()) return;

        executor.execute(() -> {
            try {
                URL url = new URL("https://onesignal.com/api/v1/notifications");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (REST_API_KEY != null && !REST_API_KEY.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Key " + REST_API_KEY);
                }

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("app_id", DEFAULT_APP_ID);

                JSONArray targetUsers = new JSONArray();
                targetUsers.put(receiverUserId);

                JSONObject aliases = new JSONObject();
                aliases.put("external_id", targetUsers);
                jsonBody.put("include_aliases", aliases);
                jsonBody.put("target_channel", "push");

                JSONObject data = new JSONObject();
                data.put("type", "INCOMING_CALL");
                data.put("callId", callId);
                data.put("callerUid", callerUid != null ? callerUid : "");
                data.put("callerName", callerName != null ? callerName : "");
                data.put("callerAvatar", callerAvatar != null ? callerAvatar : "");
                data.put("isVideo", String.valueOf(isVideo));
                data.put("action", action != null ? action : "INVITE");
                jsonBody.put("data", data);

                String title = (callerName != null && !callerName.isEmpty()) ? callerName : "FitBond Call";
                String body = isVideo ? "Incoming Video Call..." : "Incoming Voice Call...";

                jsonBody.put("headings", new JSONObject().put("en", title));
                jsonBody.put("contents", new JSONObject().put("en", body));
                jsonBody.put("priority", 10);

                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        resp.append(line);
                    }
                    Log.d(TAG, "OneSignal Call Push Response (" + responseCode + "): " + resp);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send OneSignal Call Push: " + e.getMessage(), e);
            }
        });
    }

    public void sendChatNotification(String receiverUserId, String senderName, String messageText, String senderUid) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String senderAvatar = currentUser != null && currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "";
        sendChatNotification(receiverUserId, senderName, messageText, senderUid, senderAvatar);
    }

    public void sendChatNotification(String receiverUserId, String senderName, String messageText,
                                     String senderUid, String senderAvatar) {
        if (receiverUserId == null || receiverUserId.isEmpty()) return;

        executor.execute(() -> {
            try {
                URL url = new URL("https://onesignal.com/api/v1/notifications");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (REST_API_KEY != null && !REST_API_KEY.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Key " + REST_API_KEY);
                }

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("app_id", DEFAULT_APP_ID);

                JSONArray targetUsers = new JSONArray();
                targetUsers.put(receiverUserId);

                JSONObject aliases = new JSONObject();
                aliases.put("external_id", targetUsers);
                jsonBody.put("include_aliases", aliases);
                jsonBody.put("target_channel", "push");

                JSONObject data = new JSONObject();
                data.put("type", "CHAT_MESSAGE");
                data.put("senderUid", senderUid != null ? senderUid : "");
                data.put("senderName", senderName != null ? senderName : "");
                data.put("senderAvatar", senderAvatar != null ? senderAvatar : "");
                jsonBody.put("data", data);

                String title = (senderName != null && !senderName.isEmpty()) ? senderName : "FitBond Chat";
                String body = (messageText != null && !messageText.isEmpty()) ? messageText : "New message received";

                jsonBody.put("headings", new JSONObject().put("en", title));
                jsonBody.put("contents", new JSONObject().put("en", body));

                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        resp.append(line);
                    }
                    Log.d(TAG, "OneSignal Chat Push Response (" + responseCode + "): " + resp);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send OneSignal Chat Push: " + e.getMessage(), e);
            }
        });
    }

    public void sendMissedCallNotification(String receiverUserId, String callerName, String callerUid,
                                           String callerAvatar, boolean isVideo) {
        sendMissedCallNotification(receiverUserId, callerName, callerUid, callerAvatar, null, isVideo);
    }

    public void sendMissedCallNotification(String receiverUserId, String callerName, String callerUid,
                                           String callerAvatar, String callId, boolean isVideo) {
        if (receiverUserId == null || receiverUserId.isEmpty()) return;

        executor.execute(() -> {
            try {
                URL url = new URL("https://onesignal.com/api/v1/notifications");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (REST_API_KEY != null && !REST_API_KEY.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Key " + REST_API_KEY);
                }

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("app_id", DEFAULT_APP_ID);

                JSONArray targetUsers = new JSONArray();
                targetUsers.put(receiverUserId);

                JSONObject aliases = new JSONObject();
                aliases.put("external_id", targetUsers);
                jsonBody.put("include_aliases", aliases);
                jsonBody.put("target_channel", "push");

                JSONObject data = new JSONObject();
                data.put("type", "MISSED_CALL");
                data.put("callId", callId != null ? callId : "");
                data.put("callerUid", callerUid != null ? callerUid : "");
                data.put("callerName", callerName != null ? callerName : "");
                data.put("callerAvatar", callerAvatar != null ? callerAvatar : "");
                data.put("isVideo", String.valueOf(isVideo));
                jsonBody.put("data", data);

                String title = (callerName != null && !callerName.isEmpty()) ? callerName : "Missed Call";
                String body = isVideo ? "Missed video call" : "Missed voice call";

                jsonBody.put("headings", new JSONObject().put("en", title));
                jsonBody.put("contents", new JSONObject().put("en", body));

                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        resp.append(line);
                    }
                    Log.d(TAG, "OneSignal Missed Call Push Response (" + responseCode + "): " + resp);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send OneSignal Missed Call Push: " + e.getMessage(), e);
            }
        });
    }
}
