package com.amstudio.fitup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.ActiveCallManager;
import com.amstudio.fitup.repository.TencentChatManager;
import com.amstudio.fitup.repository.UserRepository;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener;
import com.tencent.imsdk.v2.V2TIMMessage;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IndividualChatActivity extends AppCompatActivity {

    private static final String TAG = "IndividualChatActivity";

    public static final String EXTRA_CHAT_ID = "extra_chat_id";
    public static final String EXTRA_CHAT_NAME = "extra_chat_name";
    public static final String EXTRA_CHAT_INITIALS = "extra_chat_initials";
    public static final String EXTRA_GRADIENT_RES = "extra_gradient_res";
    public static final String EXTRA_IS_ONLINE = "extra_is_online";

    private View rootLayout;
    private View headerBar;
    private View composerLayout;

    private ImageButton btnBack;
    private TextView tvAvatarInitials;
    private View vOnlineDot;
    private TextView tvUserName;
    private TextView tvStatus;
    private ImageButton btnVoiceCall;
    private ImageButton btnVideoCall;

    private RecyclerView rvMessages;
    private EditText etInput;
    private ImageButton btnGallery;
    private ImageButton btnEmoji;
    private ImageButton btnSnapShortcut;
    private ImageButton btnSend;

    private ChatMessageAdapter adapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final Set<String> processedMsgIds = new HashSet<>();
    private V2TIMAdvancedMsgListener msgListener;
    private ListenerRegistration firestoreListener;

    private View activeCallBanner;
    private TextView tvBannerTitle;
    private TextView tvBannerSub;
    private TextView tvBannerAvatarInitials;
    private ActiveCallManager.OnCallStateChangeListener activeCallStateListener;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerTimerRunnable;

    private String chatId = "1";
    private String userName = "Gym Buddy";
    private String userInitials = "GB";
    private int gradientRes = R.drawable.bg_avatar_gradient_3;
    private boolean isOnline = true;

    private ActivityResultLauncher<String> galleryPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_individual_chat);

        // System Bar Window Insets setup
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        setupActivityLaunchers();
        parseIntentExtras();
        initViews();
        applyWindowInsets();
        setupHeader();
        fetchUserProfileDetails();
        setupRecyclerView();
        loadRealConversationHistory();
        setupComposer();
        setupTencentMessageListener();
        setupFirestoreRealtimeListener();
        setupActiveCallBanner();
    }

    private void setupActivityLaunchers() {
        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        sendImageFromGallery(uri);
                    }
                }
        );
    }

    private void parseIntentExtras() {
        if (getIntent() != null) {
            if (getIntent().hasExtra(EXTRA_CHAT_ID)) {
                chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
            }
            if (getIntent().hasExtra(EXTRA_CHAT_NAME)) {
                userName = getIntent().getStringExtra(EXTRA_CHAT_NAME);
            }
            if (getIntent().hasExtra(EXTRA_CHAT_INITIALS)) {
                userInitials = getIntent().getStringExtra(EXTRA_CHAT_INITIALS);
            }
            gradientRes = getIntent().getIntExtra(EXTRA_GRADIENT_RES, R.drawable.bg_avatar_gradient_3);
            isOnline = getIntent().getBooleanExtra(EXTRA_IS_ONLINE, true);
        }
    }

    private void initViews() {
        rootLayout = findViewById(R.id.chat_root_layout);
        headerBar = findViewById(R.id.chat_header_bar);
        composerLayout = findViewById(R.id.layout_chat_composer);

        btnBack = findViewById(R.id.btn_chat_back);
        tvAvatarInitials = findViewById(R.id.tv_chat_avatar_initials);
        vOnlineDot = findViewById(R.id.v_chat_online_dot);
        tvUserName = findViewById(R.id.tv_chat_user_name);
        tvStatus = findViewById(R.id.tv_chat_status);
        btnVoiceCall = findViewById(R.id.btn_chat_voice_call);
        btnVideoCall = findViewById(R.id.btn_chat_video_call);

        rvMessages = findViewById(R.id.rv_chat_messages);
        etInput = findViewById(R.id.et_chat_input);
        btnGallery = findViewById(R.id.btn_chat_gallery);
        btnEmoji = findViewById(R.id.btn_chat_emoji);
        btnSnapShortcut = findViewById(R.id.btn_chat_snap_shortcut);
        btnSend = findViewById(R.id.btn_chat_send);
    }

    private void applyWindowInsets() {
        if (rootLayout == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            float density = getResources().getDisplayMetrics().density;

            if (headerBar != null) {
                headerBar.setPadding(
                        Math.round(12 * density),
                        systemBars.top + Math.round(6 * density),
                        Math.round(16 * density),
                        Math.round(8 * density)
                );
            }

            int bottomPadding = Math.max(systemBars.bottom, imeInsets.bottom);
            if (composerLayout != null) {
                int paddingPx = Math.round(8 * density);
                composerLayout.setPadding(
                        paddingPx,
                        paddingPx,
                        paddingPx,
                        Math.max(bottomPadding + paddingPx, Math.round(12 * density))
                );
            }

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupHeader() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (tvUserName != null) {
            tvUserName.setText(userName);
        }

        if (tvAvatarInitials != null) {
            tvAvatarInitials.setText(userInitials);
            tvAvatarInitials.setBackgroundResource(gradientRes);
        }

        if (vOnlineDot != null) {
            vOnlineDot.setVisibility(isOnline ? View.VISIBLE : View.GONE);
        }

        if (tvStatus != null) {
            tvStatus.setText(isOnline ? "Online now" : "Active recently");
        }

        if (btnVoiceCall != null) {
            btnVoiceCall.setOnClickListener(v -> initiateTrtcCall(false));
        }

        if (btnVideoCall != null) {
            btnVideoCall.setOnClickListener(v -> initiateTrtcCall(true));
        }
    }

    private void initiateTrtcCall(boolean isVideoCall) {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null || chatId == null || chatId.isEmpty()) {
            Toast.makeText(this, "Cannot initiate call: User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String callId = "call_" + System.currentTimeMillis();

        ActiveCallManager.getInstance().setActiveCall(callId, chatId, userName, userInitials, isVideoCall, System.currentTimeMillis());

        Map<String, Object> callDoc = new HashMap<>();
        callDoc.put("callId", callId);
        callDoc.put("callerUid", currentUid);
        callDoc.put("receiverUid", chatId);
        callDoc.put("callerName", "You");
        callDoc.put("receiverName", userName);
        callDoc.put("callType", isVideoCall ? "VIDEO" : "VOICE");
        callDoc.put("status", "CALLING");
        callDoc.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("calls").document(callId).set(callDoc)
                .addOnSuccessListener(aVoid -> {
                    TencentChatManager.getInstance().sendTrtcCallSignalMessage(
                            chatId, callId, isVideoCall ? "VIDEO" : "VOICE", "INVITE", null
                    );

                    TRTCCallActivity.start(
                            this, callId, chatId, userName, userInitials, isVideoCall, false
                    );
                })
                .addOnFailureListener(e -> {
                    ActiveCallManager.getInstance().clearActiveCall();
                    Toast.makeText(this, "Call failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchUserProfileDetails() {
        if (chatId == null || chatId.isEmpty() || "1".equals(chatId) || "2".equals(chatId)) return;

        UserRepository.getInstance().getUserProfile(chatId, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isFinishing() && profile != null) {
                    String name = profile.getDisplayName() != null && !profile.getDisplayName().isEmpty()
                            ? profile.getDisplayName()
                            : (profile.getUsername() != null ? profile.getUsername() : "Gym Buddy");
                    userName = name;
                    userInitials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "GB";

                    if (tvUserName != null) tvUserName.setText(userName);
                    if (tvAvatarInitials != null) tvAvatarInitials.setText(userInitials);
                }
            }

            @Override
            public void onError(Exception e) {
                // Keep default header
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter(message -> {
            boolean isVideo = message.getSnapKind() == ChatMessage.SnapKind.VIDEO;
            SnapViewerDialog dialog = SnapViewerDialog.newInstance(
                    userName,
                    userInitials,
                    gradientRes,
                    isVideo,
                    message.getDurationText(),
                    message.getId(),
                    message.getMediaUri()
            );
            dialog.show(getSupportFragmentManager(), "SnapViewerDialog");
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
    }

    private void loadRealConversationHistory() {
        TencentChatManager chatManager = TencentChatManager.getInstance();
        chatManager.loginWithFirebaseUser(new TencentChatManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                chatManager.getC2CHistoryMessageList(chatId, 50, null, new TencentChatManager.MessageListCallback() {
                    @Override
                    public void onSuccess(List<V2TIMMessage> v2TIMMessages) {
                        if (isFinishing()) return;

                        if (v2TIMMessages != null && !v2TIMMessages.isEmpty()) {
                            List<ChatMessage> parsedList = new ArrayList<>();
                            for (V2TIMMessage msg : v2TIMMessages) {
                                ChatMessage parsed = parseTencentMessage(msg);
                                if (parsed != null) {
                                    if (!processedMsgIds.contains(parsed.getId())) {
                                        processedMsgIds.add(parsed.getId());
                                        parsedList.add(parsed);
                                    }
                                }
                            }
                            Collections.reverse(parsedList);
                            messageList.addAll(parsedList);

                            if (adapter != null) {
                                adapter.setMessages(messageList);
                                scrollToBottom();
                            }
                        }
                    }

                    @Override
                    public void onError(int code, String desc) {
                        Log.w(TAG, "Tencent history fetch code " + code + ": " + desc);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Tencent login error: " + e.getLocalizedMessage());
            }
        });
    }

    private void setupFirestoreRealtimeListener() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null || chatId == null || chatId.isEmpty()) return;

        String conversationId = currentUid.compareTo(chatId) < 0 ? currentUid + "_" + chatId : chatId + "_" + currentUid;

        firestoreListener = FirebaseFirestore.getInstance().collection("chats").document(conversationId)
                .collection("messages").orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || isFinishing()) return;

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            DocumentSnapshot doc = dc.getDocument();
                            String mId = doc.getId();
                            if (processedMsgIds.contains(mId)) continue;
                            processedMsgIds.add(mId);

                            String sUid = doc.getString("senderUid");
                            String text = doc.getString("text");
                            String type = doc.getString("type");
                            boolean isSelf = currentUid.equals(sUid);

                            if ("TEXT".equalsIgnoreCase(type) && text != null) {
                                ChatMessage.MessageType msgType = isSelf ? ChatMessage.MessageType.TEXT_OUTGOING : ChatMessage.MessageType.TEXT_INCOMING;
                                String senderName = isSelf ? "You" : userName;
                                ChatMessage chatMsg = new ChatMessage(mId, isSelf ? "me" : chatId, senderName, text, msgType, ChatMessage.SnapKind.NONE, getCurrentFormattedTime(), "", null, false);
                                if (adapter != null) {
                                    adapter.addMessage(chatMsg);
                                    scrollToBottom();
                                }
                            } else if ("SNAP".equalsIgnoreCase(type)) {
                                String mType = doc.getString("mediaType");
                                String mediaUrl = doc.getString("mediaUrl");
                                Long duration = doc.getLong("duration");
                                boolean isVideo = "VIDEO".equalsIgnoreCase(mType);

                                ChatMessage.MessageType msgType = isSelf ? ChatMessage.MessageType.SNAP_OUTGOING : ChatMessage.MessageType.SNAP_INCOMING;
                                ChatMessage.SnapKind kind = isVideo ? ChatMessage.SnapKind.VIDEO : ChatMessage.SnapKind.PHOTO;
                                String durationStr = isVideo ? "0:" + (duration != null ? duration : 15) : (duration != null ? duration : 10) + "s";
                                String label = isVideo ? "Video Snap" : "Snap";

                                ChatMessage snapMsg = new ChatMessage(mId, isSelf ? "me" : chatId, isSelf ? "You" : userName, label, msgType, kind, getCurrentFormattedTime(), durationStr, mediaUrl, false);
                                if (adapter != null) {
                                    adapter.addMessage(snapMsg);
                                    scrollToBottom();
                                }
                            } else if ("CALL".equalsIgnoreCase(type)) {
                                Boolean isMissed = doc.getBoolean("isMissed");
                                String callType = doc.getString("callType");
                                boolean isVideoCall = "VIDEO".equalsIgnoreCase(callType);
                                boolean missed = Boolean.TRUE.equals(isMissed);

                                ChatMessage callMsg = new ChatMessage(
                                        mId,
                                        isSelf ? "me" : chatId,
                                        isSelf ? "You" : userName,
                                        text != null ? text : "Call",
                                        ChatMessage.MessageType.CALL_EVENT,
                                        ChatMessage.SnapKind.NONE,
                                        getCurrentFormattedTime(),
                                        "",
                                        null,
                                        false,
                                        missed,
                                        isVideoCall
                                );
                                if (adapter != null) {
                                    adapter.addMessage(callMsg);
                                    scrollToBottom();
                                }
                            }
                        }
                    }
                });
    }

    private ChatMessage parseTencentMessage(V2TIMMessage msg) {
        if (msg == null) return null;

        boolean isSelf = msg.isSelf();
        String msgId = msg.getMsgID() != null ? msg.getMsgID() : "msg_" + System.currentTimeMillis();
        String senderId = isSelf ? "me" : chatId;
        String senderName = isSelf ? "You" : userName;
        String timeStr = formatTimestamp(msg.getTimestamp());

        if (msg.getElemType() == V2TIMMessage.V2TIM_ELEM_TYPE_TEXT && msg.getTextElem() != null) {
            String text = msg.getTextElem().getText();
            ChatMessage.MessageType type = isSelf ? ChatMessage.MessageType.TEXT_OUTGOING : ChatMessage.MessageType.TEXT_INCOMING;
            return new ChatMessage(msgId, senderId, senderName, text, type, ChatMessage.SnapKind.NONE, timeStr, "", null, false);
        }

        if (msg.getElemType() == V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM && msg.getCustomElem() != null) {
            try {
                byte[] data = msg.getCustomElem().getData();
                if (data != null) {
                    String jsonStr = new String(data, StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(jsonStr);

                    String msgTypeStr = json.optString("type", "");
                    if ("TRTC_CALL_SIGNAL".equalsIgnoreCase(msgTypeStr)) {
                        String action = json.optString("action", "");
                        String callType = json.optString("callType", "VOICE");
                        boolean isVideoCall = "VIDEO".equalsIgnoreCase(callType);
                        boolean isMissed = "REJECTED".equalsIgnoreCase(action) || "CANCELLED".equalsIgnoreCase(action);

                        String callText;
                        if (isMissed) {
                            callText = "Missed " + (isVideoCall ? "video" : "voice") + " call at " + timeStr.toLowerCase();
                        } else {
                            callText = (isVideoCall ? "Video" : "Voice") + " call at " + timeStr.toLowerCase();
                        }

                        return new ChatMessage(
                                msgId,
                                senderId,
                                senderName,
                                callText,
                                ChatMessage.MessageType.CALL_EVENT,
                                ChatMessage.SnapKind.NONE,
                                timeStr,
                                "",
                                null,
                                false,
                                isMissed,
                                isVideoCall
                        );
                    }

                    String snapId = json.optString("snapId", msgId);
                    String mediaType = json.optString("mediaType", "PHOTO");
                    String mediaUrl = json.isNull("mediaUrl") ? null : json.optString("mediaUrl");
                    long duration = json.optLong("duration", 10);
                    boolean isVideo = "VIDEO".equalsIgnoreCase(mediaType);

                    ChatMessage.MessageType type = isSelf ? ChatMessage.MessageType.SNAP_OUTGOING : ChatMessage.MessageType.SNAP_INCOMING;
                    ChatMessage.SnapKind kind = isVideo ? ChatMessage.SnapKind.VIDEO : ChatMessage.SnapKind.PHOTO;
                    String durationStr = isVideo ? "0:" + duration : duration + "s";
                    String label = isVideo ? "Video Snap" : "Snap";

                    return new ChatMessage(snapId, senderId, senderName, label, type, kind, timeStr, durationStr, mediaUrl, false);
                }
            } catch (Exception e) {
                // Ignore custom parse errors
            }
        }

        return null;
    }

    private void setupComposer() {
        if (etInput != null) {
            etInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendTextMessage();
                    return true;
                }
                return false;
            });

            etInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean hasText = s != null && s.toString().trim().length() > 0;
                    if (btnSend != null) {
                        btnSend.setAlpha(hasText ? 1.0f : 0.5f);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendTextMessage());
        }

        if (btnSnapShortcut != null) {
            btnSnapShortcut.setOnClickListener(v -> openSnapCamera());
        }

        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> {
                if (galleryPickerLauncher != null) {
                    galleryPickerLauncher.launch("image/*");
                }
            });
        }

        if (btnEmoji != null) {
            btnEmoji.setOnClickListener(v ->
                    Toast.makeText(this, "Emoji keyboard ready", Toast.LENGTH_SHORT).show());
        }
    }

    private void sendTextMessage() {
        if (etInput == null) return;
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        String msgId = "msg_" + System.currentTimeMillis();
        String currentTime = getCurrentFormattedTime();

        processedMsgIds.add(msgId);

        ChatMessage msg = new ChatMessage(
                msgId,
                "me",
                "You",
                text,
                ChatMessage.MessageType.TEXT_OUTGOING,
                ChatMessage.SnapKind.NONE,
                currentTime,
                "",
                null,
                false
        );

        if (adapter != null) {
            adapter.addMessage(msg);
            scrollToBottom();
        }

        etInput.setText("");

        // 1. Send live message via Tencent IM
        TencentChatManager.getInstance().sendTextMessage(
                chatId,
                text,
                new TencentChatManager.MessageCallback() {
                    @Override
                    public void onSuccess(V2TIMMessage message) {}

                    @Override
                    public void onError(int code, String desc) {
                        Log.w(TAG, "Tencent IM send notice code " + code + ": " + desc);
                    }
                }
        );

        // 2. Real-time store in Firestore for guaranteed instant delivery
        if (currentUid != null && chatId != null && !chatId.isEmpty()) {
            String conversationId = currentUid.compareTo(chatId) < 0 ? currentUid + "_" + chatId : chatId + "_" + currentUid;
            Map<String, Object> msgDoc = new HashMap<>();
            msgDoc.put("id", msgId);
            msgDoc.put("senderUid", currentUid);
            msgDoc.put("senderName", "You");
            msgDoc.put("text", text);
            msgDoc.put("type", "TEXT");
            msgDoc.put("createdAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance().collection("chats").document(conversationId)
                    .collection("messages").document(msgId)
                    .set(msgDoc);
        }
    }

    private void sendImageFromGallery(Uri imageUri) {
        if (imageUri == null) return;

        String currentTime = getCurrentFormattedTime();
        ChatMessage msg = new ChatMessage(
                "snap_" + System.currentTimeMillis(),
                "me",
                "You",
                "Photo Snap",
                ChatMessage.MessageType.SNAP_OUTGOING,
                ChatMessage.SnapKind.PHOTO,
                currentTime,
                "10s",
                imageUri.toString(),
                false
        );

        if (adapter != null) {
            adapter.addMessage(msg);
            scrollToBottom();
        }

        Toast.makeText(this, "Photo Snap sent from gallery!", Toast.LENGTH_SHORT).show();
    }

    private void openSnapCamera() {
        Intent intent = new Intent(this, SelectFriendsActivity.class);
        startActivity(intent);
    }

    private void setupTencentMessageListener() {
        msgListener = new V2TIMAdvancedMsgListener() {
            @Override
            public void onRecvNewMessage(V2TIMMessage msg) {
                if (msg == null) return;
                String sender = msg.getSender() != null ? msg.getSender() : msg.getUserID();
                if (sender != null && sender.equalsIgnoreCase(chatId)) {
                    runOnUiThread(() -> handleIncomingTencentMessage(msg));
                }
            }
        };
        TencentChatManager.getInstance().addAdvancedMsgListener(msgListener);
    }

    private void handleIncomingTencentMessage(V2TIMMessage msg) {
        checkIncomingCallSignal(msg);
        ChatMessage parsed = parseTencentMessage(msg);
        if (parsed != null && !processedMsgIds.contains(parsed.getId())) {
            processedMsgIds.add(parsed.getId());
            if (adapter != null) {
                adapter.addMessage(parsed);
                scrollToBottom();
            }
        }
    }

    private void checkIncomingCallSignal(V2TIMMessage msg) {
        if (msg == null || msg.getElemType() != V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM || msg.getCustomElem() == null) return;
        try {
            byte[] data = msg.getCustomElem().getData();
            if (data != null) {
                String jsonStr = new String(data, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonStr);
                String type = json.optString("type");
                if ("TRTC_CALL_SIGNAL".equalsIgnoreCase(type)) {
                    String action = json.optString("action");
                    String cId = json.optString("callId");
                    String cType = json.optString("callType");
                    boolean isVideoCall = "VIDEO".equalsIgnoreCase(cType);

                    if ("INVITE".equalsIgnoreCase(action) && !isFinishing()) {
                        TRTCCallActivity.start(
                                this, cId, chatId, userName, userInitials, isVideoCall, true
                        );
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        stopBannerTimer();
        if (activeCallStateListener != null) {
            ActiveCallManager.getInstance().removeListener(activeCallStateListener);
            activeCallStateListener = null;
        }
        if (msgListener != null) {
            TencentChatManager.getInstance().removeAdvancedMsgListener(msgListener);
        }
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
        super.onDestroy();
    }

    private void setupActiveCallBanner() {
        activeCallBanner = findViewById(R.id.card_active_call_banner);
        if (activeCallBanner == null) return;

        tvBannerTitle = findViewById(R.id.tv_banner_call_title);
        tvBannerSub = findViewById(R.id.tv_banner_call_sub);
        tvBannerAvatarInitials = findViewById(R.id.tv_banner_avatar_initials);

        activeCallStateListener = (isActive, callId, name, initials, isVideo, startTime) -> {
            runOnUiThread(() -> {
                if (isActive && callId != null) {
                    activeCallBanner.setVisibility(View.VISIBLE);
                    if (tvBannerTitle != null) {
                        tvBannerTitle.setText("Ongoing " + (isVideo ? "Video" : "Voice") + " Call • " + name);
                    }
                    if (tvBannerAvatarInitials != null) {
                        tvBannerAvatarInitials.setText(initials != null ? initials : "GB");
                    }

                    activeCallBanner.setOnClickListener(v -> {
                        TRTCCallActivity.start(
                                this, callId, "", name, initials, isVideo, false
                        );
                    });

                    startBannerTimer(startTime);
                } else {
                    activeCallBanner.setVisibility(View.GONE);
                    stopBannerTimer();
                }
            });
        };

        ActiveCallManager.getInstance().addListener(activeCallStateListener);
    }

    private void startBannerTimer(long startTimeMs) {
        stopBannerTimer();
        bannerTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (tvBannerSub != null && ActiveCallManager.getInstance().isActive()) {
                    long elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000;
                    long min = elapsedSec / 60;
                    long sec = elapsedSec % 60;
                    tvBannerSub.setText(String.format(Locale.US, "Tap to return to call • %02d:%02d", min, sec));
                    bannerHandler.postDelayed(this, 1000);
                }
            }
        };
        bannerHandler.post(bannerTimerRunnable);
    }

    private void stopBannerTimer() {
        if (bannerTimerRunnable != null) {
            bannerHandler.removeCallbacks(bannerTimerRunnable);
            bannerTimerRunnable = null;
        }
    }

    private String formatTimestamp(long epochSeconds) {
        if (epochSeconds <= 0) return getCurrentFormattedTime();
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        return sdf.format(new Date(epochSeconds * 1000L));
    }

    private String getCurrentFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        return sdf.format(new Date());
    }

    private void scrollToBottom() {
        if (rvMessages != null && adapter != null && adapter.getItemCount() > 0) {
            rvMessages.post(() -> rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1));
        }
    }
}
