package com.amstudio.fitup;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.ActiveCallManager;
import com.amstudio.fitup.repository.ChatCacheManager;
import com.amstudio.fitup.repository.ImgBBUploader;
import com.amstudio.fitup.repository.TencentChatManager;
import com.amstudio.fitup.repository.UserRepository;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener;
import com.tencent.imsdk.v2.V2TIMMessage;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private int gradientRes = R.drawable.bg_avatar_gradient_1;
    private boolean isOnline = true;

    private FrameLayout emojiKeyboardContainer;
    private View emojiKeyboardView;
    private int selectedInlineEmojiCategory = 0;

    private static final String[][] EMOJI_CATEGORIES = {
            // Category 0: Recent & Popular
            {
                    "😭", "😉", "😗", "😘", "🥰", "😍", "😊", "😂", "🤣", "🔥", "❤️", "👍", "👏", "🥳", "🥺",
                    "💯", "✨", "🙏", "😎", "😏", "🤔", "🤫", "😜", "😋", "💖", "🎉", "💩", "💀", "🤩", "😁"
            },
            // Category 1: Smileys & Emotions
            {
                    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉",
                    "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓",
                    "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫",
                    "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰",
                    "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
                    "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕",
                    "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃"
            },
            // Category 2: People & Gestures
            {
                    "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉",
                    "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                    "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷",
                    "🦴", "👀", "👁️", "👅", "👄", "👶", "🧒", "👦", "👧", "🧑", "👨", "👩", "🧔", "👱‍♂️", "👱‍♀️"
            },
            // Category 3: Activities & Celebrations
            {
                    "🎉", "🥳", "🎊", "🎈", "🎁", "🎂", "🍰", "🧁", "🍿", "🍕", "🍔", "🍟", "🌭", "🥪", "🌮",
                    "🌯", "🍝", "🍜", "🍣", "🍱", "🍦", "🍩", "🍪", "🍫", "🍬", "🍭", "☕️", "🧃", "🧋", "🍺"
            },
            // Category 4: Sports & Games
            {
                    "⚽️", "🏀", "🏈", "⚾️", "🥎", "🎾", "🏐", "🏉", "🏓", "🏸", "🏒", "🏏", "🥊", "🥋", "🛹",
                    "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎯", "🎲", "♟️", "🎳", "🎮", "🎰", "🧩", "🎯", "🚴"
            },
            // Category 5: Travel & Places
            {
                    "🚗", "🚕", "🚙", "🚌", "🏎️", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍️", "🛺",
                    "🚲", "🛴", "🚨", "🚔", "🚘", "🚃", "🚋", "🚞", "🚄", "🚅", "✈️", "🛫", "🛬", "🛸", "🚀"
            },
            // Category 6: Hearts & Symbols
            {
                    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗",
                    "💖", "💘", "💝", "💟", "💯", "💢", "💥", "🔥", "✨", "🌟", "💫", "⭐️", "✅", "❇️", "⚡️"
            }
    };

    private static final String[] EMOJI_CATEGORY_ICONS = {"🕒", "😃", "🙋", "🎉", "⚽", "🚗", "🔣"};

    private ActivityResultLauncher<String> galleryPickerLauncher;
    private ActivityResultLauncher<Void> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (emojiKeyboardContainer != null && emojiKeyboardContainer.getVisibility() == View.VISIBLE) {
                    hideEmojiPicker();
                } else {
                    finish();
                }
            }
        });

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

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        Uri uri = getImageUriFromBitmap(bitmap);
                        if (uri != null) {
                            sendImageFromGallery(uri);
                        }
                    }
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchCameraIntent();
                    } else {
                        Toast.makeText(this, "Camera permission is required to capture photos", Toast.LENGTH_SHORT).show();
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
            gradientRes = getIntent().getIntExtra(EXTRA_GRADIENT_RES, R.drawable.bg_avatar_gradient_1);
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
        emojiKeyboardContainer = findViewById(R.id.layout_emoji_picker_keyboard);
    }

    private void applyWindowInsets() {
        if (rootLayout == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            float density = getResources().getDisplayMetrics().density;

            if (headerBar != null) {
                headerBar.setPadding(
                        Math.round(10 * density),
                        systemBars.top + Math.round(6 * density),
                        Math.round(12 * density),
                        Math.round(8 * density)
                );
            }

            boolean isEmojiVisible = emojiKeyboardContainer != null && emojiKeyboardContainer.getVisibility() == View.VISIBLE;
            int bottomInset = isEmojiVisible ? 0 : Math.max(systemBars.bottom, imeInsets.bottom);

            if (composerLayout != null) {
                ViewGroup.LayoutParams params = composerLayout.getLayoutParams();
                if (params instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;
                    int marginPx = Math.round(10 * density);
                    lp.leftMargin = marginPx;
                    lp.rightMargin = marginPx;
                    lp.bottomMargin = isEmojiVisible ? Math.round(6 * density) : (marginPx + bottomInset);
                    composerLayout.setLayoutParams(lp);
                }
                int paddingHorizontal = Math.round(8 * density);
                int paddingVertical = Math.round(6 * density);
                composerLayout.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            }

            if (emojiKeyboardContainer != null) {
                emojiKeyboardContainer.setPadding(0, 0, 0, systemBars.bottom);
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
                    if (adapter != null) adapter.setFriendDetails(userInitials, gradientRes);

                    isOnline = profile.isOnline();
                    if (vOnlineDot != null) {
                        vOnlineDot.setVisibility(isOnline ? View.VISIBLE : View.GONE);
                    }
                    if (tvStatus != null) {
                        tvStatus.setText(isOnline ? "active now" : "active recently");
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                // Keep default header
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter(new ChatMessageAdapter.OnSnapClickListener() {
            @Override
            public void onSnapClick(ChatMessage message) {
                onSnapClick(message, 0);
            }

            @Override
            public void onSnapClick(ChatMessage message, int startIndex) {
                boolean isVideo = message.getSnapKind() == ChatMessage.SnapKind.VIDEO;
                if (message.isSnap()) {
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
                } else {
                    ArrayList<String> uris = new ArrayList<>();
                    if (message.getMediaUris() != null && !message.getMediaUris().isEmpty()) {
                        uris.addAll(message.getMediaUris());
                    } else if (message.getMediaUri() != null && !message.getMediaUri().isEmpty()) {
                        uris.add(message.getMediaUri());
                    }
                    if (!uris.isEmpty()) {
                        int validIndex = (startIndex >= 0 && startIndex < uris.size()) ? startIndex : 0;
                        ImageViewerActivity.open(IndividualChatActivity.this, uris, validIndex, userName);
                    }
                }
            }
        });
        adapter.setFriendDetails(userInitials, gradientRes);

        adapter.setOnMessageLongClickListener((message, anchorView) -> showReactionPicker(message, anchorView));
        adapter.setOnReactionClickListener((message, emoji) -> toggleReactionOnMessage(message, emoji));

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        loadCachedMessages();
    }

    private void showReactionPicker(ChatMessage message, View anchorView) {
        if (message == null || isFinishing()) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_reaction_picker);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View.OnClickListener clickListener = v -> {
            TextView tv = (TextView) v;
            String emoji = tv.getText().toString();
            toggleReactionOnMessage(message, emoji);
            dialog.dismiss();
        };

        dialog.findViewById(R.id.tv_react_heart).setOnClickListener(clickListener);
        dialog.findViewById(R.id.tv_react_fire).setOnClickListener(clickListener);
        dialog.findViewById(R.id.tv_react_ghost).setOnClickListener(clickListener);
        dialog.findViewById(R.id.tv_react_thumbs).setOnClickListener(clickListener);
        dialog.findViewById(R.id.tv_react_laugh).setOnClickListener(clickListener);
        dialog.findViewById(R.id.tv_react_surprised).setOnClickListener(clickListener);

        dialog.show();
    }

    private void toggleReactionOnMessage(ChatMessage message, String selectedEmoji) {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null || chatId == null || message == null || message.getId() == null) return;

        // 1. Optimistic local update (Instant UI feedback on screen)
        String prevEmoji = message.getUserReactionEmoji();
        List<ChatMessage.Reaction> currentList = new ArrayList<>(message.getReactions());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ChatMessage.Reaction r : currentList) {
            counts.put(r.getEmoji(), r.getCount());
        }

        // Remove user's previous reaction if exists
        if (prevEmoji != null && counts.containsKey(prevEmoji)) {
            Integer prevCount = counts.get(prevEmoji);
            if (prevCount != null) {
                int newCount = prevCount - 1;
                if (newCount > 0) {
                    counts.put(prevEmoji, newCount);
                } else {
                    counts.remove(prevEmoji);
                }
            }
        }

        // If selected emoji is different, add user's new reaction.
        // If selected emoji equals previous emoji, user toggled off (removed) their reaction.
        if (!selectedEmoji.equals(prevEmoji)) {
            Integer existing = counts.get(selectedEmoji);
            int existingCount = existing != null ? existing : 0;
            counts.put(selectedEmoji, existingCount + 1);
            message.setUserReactionEmoji(selectedEmoji);
        } else {
            message.setUserReactionEmoji(null); // Toggled off / removed
        }

        // Build updated reaction list
        List<ChatMessage.Reaction> newList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            newList.add(new ChatMessage.Reaction(entry.getKey(), entry.getValue()));
        }

        message.setReactions(newList);

        // Notify adapter immediately for instant visual feedback
        if (adapter != null) {
            int pos = messageList.indexOf(message);
            if (pos >= 0) {
                adapter.notifyItemChanged(pos);
            } else {
                adapter.notifyDataSetChanged();
            }
        }

        saveCurrentMessagesToCache();

        // 2. Sync reaction state to Firestore
        if (message.getId() != null) {
            String conversationId = currentUid.compareTo(chatId) < 0 ? currentUid + "_" + chatId : chatId + "_" + currentUid;
            DocumentReference msgRef = FirebaseFirestore.getInstance()
                    .collection("chats").document(conversationId)
                    .collection("messages").document(message.getId());

            FirebaseFirestore.getInstance().runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(msgRef);
                Map<String, Object> reactionsMap = null;
                if (snapshot.exists()) {
                    Object obj = snapshot.get("reactions");
                    if (obj instanceof Map) {
                        reactionsMap = new HashMap<>((Map<String, Object>) obj);
                    }
                }
                if (reactionsMap == null) {
                    reactionsMap = new HashMap<>();
                }

                // 1. Find if current user has already reacted with any emoji in Firestore
                String previousEmoji = null;
                for (Map.Entry<String, Object> entry : reactionsMap.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof List) {
                        List<String> uids = (List<String>) val;
                        if (uids.contains(currentUid)) {
                            previousEmoji = entry.getKey();
                            break;
                        }
                    }
                }

                // 2. Remove ONLY current user from previous emoji (friends' reactions stay untouched)
                if (previousEmoji != null) {
                    Object val = reactionsMap.get(previousEmoji);
                    if (val instanceof List) {
                        List<String> uids = new ArrayList<>((List<String>) val);
                        uids.remove(currentUid);
                        if (uids.isEmpty()) {
                            reactionsMap.remove(previousEmoji);
                        } else {
                            reactionsMap.put(previousEmoji, uids);
                        }
                    }
                }

                // 3. If selected emoji is different, add current user to new emoji
                if (!selectedEmoji.equals(previousEmoji)) {
                    Object val = reactionsMap.get(selectedEmoji);
                    List<String> uids;
                    if (val instanceof List) {
                        uids = new ArrayList<>((List<String>) val);
                    } else {
                        uids = new ArrayList<>();
                    }
                    if (!uids.contains(currentUid)) {
                        uids.add(currentUid);
                    }
                    reactionsMap.put(selectedEmoji, uids);
                }

                if (snapshot.exists()) {
                    transaction.update(msgRef, "reactions", reactionsMap);
                } else {
                    Map<String, Object> newDoc = new HashMap<>();
                    newDoc.put("id", message.getId());
                    newDoc.put("senderUid", message.isOutgoing() ? currentUid : chatId);
                    newDoc.put("senderName", message.getSenderName());
                    newDoc.put("text", message.getText());
                    newDoc.put("type", message.getSnapKind() != ChatMessage.SnapKind.NONE || message.getMediaUri() != null ? "IMAGE" : "TEXT");
                    newDoc.put("createdAt", FieldValue.serverTimestamp());
                    newDoc.put("reactions", reactionsMap);
                    transaction.set(msgRef, newDoc, SetOptions.merge());
                }
                return null;
            }).addOnFailureListener(e -> Log.w(TAG, "Reaction sync error: " + e.getMessage()));
        }
    }

    private void parseAndApplyReactionsFromDoc(DocumentSnapshot doc, ChatMessage msg, String currentUid) {
        if (doc == null || msg == null) return;
        List<ChatMessage.Reaction> reactionList = new ArrayList<>();
        String myReaction = null;

        Object obj = doc.get("reactions");
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String emoji = entry.getKey();
                Object val = entry.getValue();
                int count = 0;
                if (val instanceof List) {
                    List<String> uids = (List<String>) val;
                    count = uids.size();
                    if (currentUid != null && uids.contains(currentUid)) {
                        myReaction = emoji;
                    }
                } else if (val instanceof Number) {
                    count = ((Number) val).intValue();
                }
                if (count > 0) {
                    reactionList.add(new ChatMessage.Reaction(emoji, count));
                }
            }
        }
        msg.setReactions(reactionList);
        msg.setUserReactionEmoji(myReaction);
    }

    private void loadCachedMessages() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid != null && chatId != null) {
            List<ChatMessage> cachedMsgs = ChatCacheManager.getCachedChatMessages(this, currentUid, chatId);
            if (cachedMsgs != null && !cachedMsgs.isEmpty()) {
                messageList.clear();
                processedMsgIds.clear();
                for (ChatMessage m : cachedMsgs) {
                    if (m != null && m.getId() != null) {
                        messageList.add(m);
                        processedMsgIds.add(m.getId());
                    }
                }
                if (adapter != null) {
                    adapter.setMessages(messageList);
                    scrollToBottom();
                }
            }
        }
    }

    private void saveCurrentMessagesToCache() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid != null && chatId != null && !messageList.isEmpty()) {
            ChatCacheManager.saveChatMessages(this, currentUid, chatId, messageList);
        }
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
                            saveCurrentMessagesToCache();
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
                        DocumentSnapshot doc = dc.getDocument();
                        String mId = doc.getId();

                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            int existingIdx = -1;
                            for (int i = 0; i < messageList.size(); i++) {
                                if (mId.equals(messageList.get(i).getId())) {
                                    existingIdx = i;
                                    break;
                                }
                            }
                            if (existingIdx >= 0) {
                                parseAndApplyReactionsFromDoc(doc, messageList.get(existingIdx), currentUid);
                                if (adapter != null) {
                                    adapter.notifyItemChanged(existingIdx);
                                }
                                continue;
                            }

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
                                parseAndApplyReactionsFromDoc(doc, chatMsg, currentUid);
                                if (adapter != null) {
                                    adapter.addMessage(chatMsg);
                                    scrollToBottom();
                                }
                            } else if ("SNAP".equalsIgnoreCase(type) || "IMAGE".equalsIgnoreCase(type)) {
                                String mType = doc.getString("mediaType");
                                String mediaUrl = doc.getString("mediaUrl");
                                Long duration = doc.getLong("duration");
                                boolean isVideo = "VIDEO".equalsIgnoreCase(mType);

                                ChatMessage.MessageType msgType;
                                ChatMessage.SnapKind kind;
                                if (isVideo) {
                                    msgType = isSelf ? ChatMessage.MessageType.SNAP_OUTGOING : ChatMessage.MessageType.SNAP_INCOMING;
                                    kind = ChatMessage.SnapKind.VIDEO;
                                } else {
                                    msgType = isSelf ? ChatMessage.MessageType.IMAGE_OUTGOING : ChatMessage.MessageType.IMAGE_INCOMING;
                                    kind = ChatMessage.SnapKind.NONE;
                                }

                                String durationStr = isVideo ? "0:" + (duration != null ? duration : 15) : "";
                                String label = isVideo ? "Video Snap" : "Photo";

                                ChatMessage snapMsg = new ChatMessage(mId, isSelf ? "me" : chatId, isSelf ? "You" : userName, label, msgType, kind, getCurrentFormattedTime(), durationStr, mediaUrl, false);
                                parseAndApplyReactionsFromDoc(doc, snapMsg, currentUid);
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
                                parseAndApplyReactionsFromDoc(doc, callMsg, currentUid);
                                if (adapter != null) {
                                    adapter.addMessage(callMsg);
                                    scrollToBottom();
                                }
                            }
                        } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                            for (int i = 0; i < messageList.size(); i++) {
                                ChatMessage msg = messageList.get(i);
                                if (mId.equals(msg.getId())) {
                                    parseAndApplyReactionsFromDoc(doc, msg, currentUid);
                                    if (adapter != null) {
                                        adapter.notifyItemChanged(i);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    saveCurrentMessagesToCache();
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

        if (msg.getElemType() == V2TIMMessage.V2TIM_ELEM_TYPE_IMAGE && msg.getImageElem() != null) {
            String imageUrl = null;
            if (msg.getImageElem().getImageList() != null && !msg.getImageElem().getImageList().isEmpty()) {
                imageUrl = msg.getImageElem().getImageList().get(0).getUrl();
            }
            ChatMessage.MessageType type = isSelf ? ChatMessage.MessageType.IMAGE_OUTGOING : ChatMessage.MessageType.IMAGE_INCOMING;
            return new ChatMessage(msgId, senderId, senderName, "Photo", type, ChatMessage.SnapKind.NONE, timeStr, "", imageUrl, false);
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

                    ChatMessage.MessageType type;
                    ChatMessage.SnapKind kind;
                    if (isVideo) {
                        type = isSelf ? ChatMessage.MessageType.SNAP_OUTGOING : ChatMessage.MessageType.SNAP_INCOMING;
                        kind = ChatMessage.SnapKind.VIDEO;
                    } else {
                        type = isSelf ? ChatMessage.MessageType.IMAGE_OUTGOING : ChatMessage.MessageType.IMAGE_INCOMING;
                        kind = ChatMessage.SnapKind.NONE;
                    }

                    String durationStr = isVideo ? "0:" + duration : "";
                    String label = isVideo ? "Video Snap" : "Photo";

                    return new ChatMessage(snapId, senderId, senderName, label, type, kind, timeStr, durationStr, mediaUrl, false);
                }
            } catch (Exception e) {
                // Ignore custom parse errors
            }
        }

        return null;
    }

    private void setupComposer() {
        boolean initialHasText = etInput != null && etInput.getText() != null && !etInput.getText().toString().trim().isEmpty();
        if (btnSend != null) {
            btnSend.setVisibility(initialHasText ? View.VISIBLE : View.GONE);
        }
        if (btnSnapShortcut != null) {
            btnSnapShortcut.setVisibility(initialHasText ? View.GONE : View.VISIBLE);
        }

        if (etInput != null) {
            etInput.setOnClickListener(v -> hideEmojiPicker());
            etInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    hideEmojiPicker();
                }
            });
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
                    boolean hasText = s != null && !s.toString().trim().isEmpty();
                    if (btnSend != null) {
                        btnSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                    }
                    if (btnSnapShortcut != null) {
                        btnSnapShortcut.setVisibility(hasText ? View.GONE : View.VISIBLE);
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
            btnEmoji.setOnClickListener(v -> toggleEmojiPicker());
        }
    }

    private void toggleEmojiPicker() {
        if (emojiKeyboardContainer != null && emojiKeyboardContainer.getVisibility() == View.VISIBLE) {
            hideEmojiPicker();
        } else {
            showEmojiPicker();
        }
    }

    private void showEmojiPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && etInput != null) {
            imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
        }

        if (emojiKeyboardContainer == null) return;

        if (emojiKeyboardView == null) {
            emojiKeyboardView = getLayoutInflater().inflate(R.layout.dialog_emoji_picker, emojiKeyboardContainer, false);
            emojiKeyboardContainer.addView(emojiKeyboardView);
            setupInlineEmojiPickerView(emojiKeyboardView);
        }

        emojiKeyboardContainer.setVisibility(View.VISIBLE);
        if (rootLayout != null) {
            ViewCompat.requestApplyInsets(rootLayout);
        }
        if (rvMessages != null && adapter != null && adapter.getItemCount() > 0) {
            rvMessages.postDelayed(() -> rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1), 100);
        }
    }

    private void hideEmojiPicker() {
        if (emojiKeyboardContainer != null && emojiKeyboardContainer.getVisibility() == View.VISIBLE) {
            emojiKeyboardContainer.setVisibility(View.GONE);
            if (rootLayout != null) {
                ViewCompat.requestApplyInsets(rootLayout);
            }
        }
    }

    private void setupInlineEmojiPickerView(View view) {
        RecyclerView rvGrid = view.findViewById(R.id.rv_emoji_grid);
        LinearLayout layoutCategories = view.findViewById(R.id.layout_emoji_categories);
        ImageButton btnDismiss = view.findViewById(R.id.btn_emoji_dismiss);
        ImageButton btnBackspace = view.findViewById(R.id.btn_emoji_backspace);
        EditText etSearch = view.findViewById(R.id.et_emoji_search);

        rvGrid.setLayoutManager(new GridLayoutManager(this, 8));
        InlineEmojiAdapter inlineAdapter = new InlineEmojiAdapter(new ArrayList<>(), emoji -> {
            if (etInput == null) return;
            int start = Math.max(etInput.getSelectionStart(), 0);
            int end = Math.max(etInput.getSelectionEnd(), 0);
            etInput.getText().replace(Math.min(start, end), Math.max(start, end), emoji, 0, emoji.length());
        });
        rvGrid.setAdapter(inlineAdapter);

        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> hideEmojiPicker());
        }

        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                if (etInput == null) return;
                etInput.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                etInput.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String q = s != null ? s.toString().trim() : "";
                    if (q.isEmpty()) {
                        inlineAdapter.setEmojis(Arrays.asList(EMOJI_CATEGORIES[selectedInlineEmojiCategory]));
                    } else {
                        List<String> all = new ArrayList<>();
                        for (String[] cat : EMOJI_CATEGORIES) {
                            for (String e : cat) {
                                if (!all.contains(e)) all.add(e);
                            }
                        }
                        inlineAdapter.setEmojis(all);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (layoutCategories != null) {
            layoutCategories.removeAllViews();
            for (int i = 0; i < EMOJI_CATEGORY_ICONS.length; i++) {
                final int catIdx = i;
                TextView tvTab = new TextView(this);
                tvTab.setText(EMOJI_CATEGORY_ICONS[i]);
                tvTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                tvTab.setPadding(16, 8, 16, 8);
                tvTab.setAlpha(i == 0 ? 1.0f : 0.55f);
                tvTab.setOnClickListener(v -> {
                    selectedInlineEmojiCategory = catIdx;
                    if (etSearch != null && etSearch.getText().length() > 0) {
                        etSearch.setText("");
                    }
                    inlineAdapter.setEmojis(Arrays.asList(EMOJI_CATEGORIES[catIdx]));
                    for (int k = 0; k < layoutCategories.getChildCount(); k++) {
                        View child = layoutCategories.getChildAt(k);
                        child.setAlpha(k == catIdx ? 1.0f : 0.55f);
                    }
                });
                layoutCategories.addView(tvTab);
            }
        }

        inlineAdapter.setEmojis(Arrays.asList(EMOJI_CATEGORIES[0]));
    }

    private interface OnInlineEmojiClickListener {
        void onEmojiClick(String emoji);
    }

    private static class InlineEmojiAdapter extends RecyclerView.Adapter<InlineEmojiAdapter.ViewHolder> {
        private final List<String> items = new ArrayList<>();
        private final OnInlineEmojiClickListener listener;

        public InlineEmojiAdapter(List<String> items, OnInlineEmojiClickListener listener) {
            if (items != null) this.items.addAll(items);
            this.listener = listener;
        }

        public void setEmojis(List<String> newItems) {
            this.items.clear();
            if (newItems != null) this.items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emoji, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String emoji = items.get(position);
            holder.tvEmoji.setText(emoji);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onEmojiClick(emoji);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvEmoji;
            ViewHolder(View itemView) {
                super(itemView);
                tvEmoji = itemView.findViewById(R.id.tv_emoji);
            }
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
        saveCurrentMessagesToCache();

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

    private Uri getImageUriFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            File cachePath = new File(getCacheDir(), "images");
            if (!cachePath.exists() && !cachePath.mkdirs()) {
                Log.w(TAG, "Failed to create directory for cached images");
            }
            File file = new File(cachePath, "captured_image_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            stream.close();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save captured image", e);
            return null;
        }
    }

    private void sendImageFromGallery(Uri imageUri) {
        if (imageUri == null) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        String msgId = "img_" + System.currentTimeMillis();
        String currentTime = getCurrentFormattedTime();

        processedMsgIds.add(msgId);

        // Show local preview immediately for sender (zero UI delay)
        ChatMessage localMsg = new ChatMessage(
                msgId,
                "me",
                "You",
                "Photo",
                ChatMessage.MessageType.IMAGE_OUTGOING,
                ChatMessage.SnapKind.NONE,
                currentTime,
                "",
                imageUri.toString(),
                false
        );

        if (adapter != null) {
            adapter.addMessage(localMsg);
            scrollToBottom();
        }
        saveCurrentMessagesToCache();

        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

        // Upload image to ImgBB (https://api.imgbb.com/1/upload)
        ImgBBUploader.uploadImage(this, imageUri, new ImgBBUploader.UploadCallback() {
            @Override
            public void onSuccess(String uploadedUrl) {
                Log.d(TAG, "Image successfully uploaded to ImgBB: " + uploadedUrl);

                // Send ImgBB URL via Tencent IM
                TencentChatManager.getInstance().sendImageMessage(
                        chatId,
                        uploadedUrl,
                        new TencentChatManager.MessageCallback() {
                            @Override
                            public void onSuccess(V2TIMMessage message) {}

                            @Override
                            public void onError(int code, String desc) {
                                Log.w(TAG, "Tencent IM send image code " + code + ": " + desc);
                            }
                        }
                );

                // Save ImgBB URL to Firestore for realtime sync to recipient
                if (currentUid != null && chatId != null && !chatId.isEmpty()) {
                    String conversationId = currentUid.compareTo(chatId) < 0 ? currentUid + "_" + chatId : chatId + "_" + currentUid;
                    Map<String, Object> msgDoc = new HashMap<>();
                    msgDoc.put("id", msgId);
                    msgDoc.put("senderUid", currentUid);
                    msgDoc.put("senderName", "You");
                    msgDoc.put("text", "Photo");
                    msgDoc.put("type", "IMAGE");
                    msgDoc.put("mediaType", "PHOTO");
                    msgDoc.put("mediaUrl", uploadedUrl);
                    msgDoc.put("createdAt", FieldValue.serverTimestamp());

                    FirebaseFirestore.getInstance().collection("chats").document(conversationId)
                            .collection("messages").document(msgId)
                            .set(msgDoc);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ImgBB upload error, falling back to local URI", e);
                Toast.makeText(IndividualChatActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openSnapCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (cameraPermissionLauncher != null) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        } else {
            launchCameraIntent();
        }
    }

    private void launchCameraIntent() {
        try {
            if (cameraLauncher != null) {
                cameraLauncher.launch(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch camera", e);
            Toast.makeText(this, "Unable to open camera: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
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
        activeCallBanner = findViewById(R.id.layout_active_call_banner);
        if (activeCallBanner == null) {
            activeCallBanner = findViewById(R.id.card_active_call_banner);
        }
        if (activeCallBanner == null) return;

        tvBannerTitle = activeCallBanner.findViewById(R.id.tv_banner_call_title);
        tvBannerSub = activeCallBanner.findViewById(R.id.tv_banner_call_sub);
        tvBannerAvatarInitials = activeCallBanner.findViewById(R.id.tv_banner_avatar_initials);
        ImageView ivCallIcon = activeCallBanner.findViewById(R.id.iv_banner_call_icon);

        View btnSpeaker = activeCallBanner.findViewById(R.id.btn_banner_speaker);
        View btnCamera = activeCallBanner.findViewById(R.id.btn_banner_camera);
        View btnMute = activeCallBanner.findViewById(R.id.btn_banner_mute);
        View btnEndCall = activeCallBanner.findViewById(R.id.btn_banner_end_call);
        View btnDropdown = activeCallBanner.findViewById(R.id.btn_banner_dropdown);
        View layoutActions = activeCallBanner.findViewById(R.id.layout_banner_actions);

        if (btnDropdown != null && layoutActions != null) {
            btnDropdown.setOnClickListener(v -> {
                boolean isCurrentlyVisible = layoutActions.getVisibility() == View.VISIBLE;
                if (isCurrentlyVisible) {
                    layoutActions.setVisibility(View.GONE);
                    if (btnDropdown instanceof ImageButton) {
                        ((ImageButton) btnDropdown).setImageResource(R.drawable.ic_chevron_down);
                    }
                } else {
                    layoutActions.setVisibility(View.VISIBLE);
                    if (btnDropdown instanceof ImageButton) {
                        ((ImageButton) btnDropdown).setImageResource(R.drawable.ic_chevron_up);
                    }
                }
            });
        }

        if (btnSpeaker != null) {
            btnSpeaker.setOnClickListener(v -> ActiveCallManager.getInstance().toggleSpeaker());
        }
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> ActiveCallManager.getInstance().toggleCamera());
        }
        if (btnMute != null) {
            btnMute.setOnClickListener(v -> ActiveCallManager.getInstance().toggleMute());
        }
        if (btnEndCall != null) {
            btnEndCall.setOnClickListener(v -> ActiveCallManager.getInstance().endCall());
        }

        View layoutText = activeCallBanner.findViewById(R.id.layout_banner_text);
        View layoutAvatar = activeCallBanner.findViewById(R.id.layout_banner_avatar);

        View.OnClickListener expandListener = v -> {
            String cId = ActiveCallManager.getInstance().getActiveCallId();
            if (cId != null) {
                String targetUid = ActiveCallManager.getInstance().getTargetUid();
                String name = ActiveCallManager.getInstance().getUserName();
                String initials = ActiveCallManager.getInstance().getUserInitials();
                boolean isVideo = ActiveCallManager.getInstance().isVideo();
                TRTCCallActivity.start(this, cId, targetUid != null ? targetUid : "", name, initials, isVideo, false);
            }
        };

        if (layoutText != null) layoutText.setOnClickListener(expandListener);
        if (layoutAvatar != null) layoutAvatar.setOnClickListener(expandListener);
        activeCallBanner.setOnClickListener(null);


        activeCallStateListener = (isActive, callId, name, initials, isVideo, startTime) -> {
            runOnUiThread(() -> {
                if (isActive && callId != null) {
                    activeCallBanner.setVisibility(View.VISIBLE);
                    View rootLayout = findViewById(R.id.chat_root_layout);
                    if (rootLayout != null) ViewCompat.requestApplyInsets(rootLayout);

                    if (tvBannerTitle != null) {
                        tvBannerTitle.setText(name != null && !name.isEmpty() ? name : "Ongoing Call");
                    }
                    if (tvBannerAvatarInitials != null) {
                        tvBannerAvatarInitials.setText(initials != null ? initials : "GB");
                    }
                    if (ivCallIcon != null) {
                        ivCallIcon.setImageResource(isVideo ? R.drawable.ic_video_call : R.drawable.ic_phone_call);
                    }

                    boolean speakerOn = ActiveCallManager.getInstance().isSpeakerOn();
                    boolean cameraOn = ActiveCallManager.getInstance().isCameraOn();
                    boolean muteOn = ActiveCallManager.getInstance().isMuteOn();

                    if (btnSpeaker != null) {
                        btnSpeaker.setBackgroundResource(speakerOn ? R.drawable.bg_call_btn_white : R.drawable.bg_call_btn_dark);
                        if (btnSpeaker instanceof ImageButton) {
                            ((ImageButton) btnSpeaker).setColorFilter(speakerOn ? Color.BLACK : Color.WHITE);
                            ((ImageButton) btnSpeaker).setImageResource(speakerOn ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
                        }
                    }

                    if (btnCamera != null) {
                        btnCamera.setBackgroundResource(cameraOn ? R.drawable.bg_call_btn_dark : R.drawable.bg_call_btn_dark_active);
                        if (btnCamera instanceof ImageButton) {
                            ((ImageButton) btnCamera).setImageResource(cameraOn ? R.drawable.ic_video_call : R.drawable.ic_video_off);
                        }
                    }

                    if (btnMute != null) {
                        btnMute.setBackgroundResource(muteOn ? R.drawable.bg_call_btn_dark_active : R.drawable.bg_call_btn_dark);
                        if (btnMute instanceof ImageButton) {
                            ((ImageButton) btnMute).setImageResource(muteOn ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
                        }
                    }

                    startBannerTimer(startTime, isVideo);
                } else {
                    activeCallBanner.setVisibility(View.GONE);
                    View rootLayout = findViewById(R.id.chat_root_layout);
                    if (rootLayout != null) ViewCompat.requestApplyInsets(rootLayout);
                    stopBannerTimer();
                }
            });
        };

        ActiveCallManager.getInstance().addListener(activeCallStateListener);
    }

    private void startBannerTimer(long startTimeMs, boolean isVideo) {
        stopBannerTimer();
        bannerTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (tvBannerSub != null && ActiveCallManager.getInstance().isActive()) {
                    long elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000;
                    long min = elapsedSec / 60;
                    long sec = elapsedSec % 60;
                    String callTypeStr = isVideo ? "FitUp Video" : "Voice Call";
                    tvBannerSub.setText(String.format(Locale.US, "%s • %02d:%02d", callTypeStr, min, sec));
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
