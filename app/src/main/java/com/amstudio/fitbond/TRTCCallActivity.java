package com.amstudio.fitbond;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.amstudio.fitbond.helper.NotificationHelper;
import com.amstudio.fitbond.helper.OneSignalManager;
import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.ActiveCallManager;
import com.amstudio.fitbond.repository.TencentChatManager;
import com.amstudio.fitbond.service.CallForegroundService;
import com.amstudio.fitbond.repository.UserRepository;
import com.amstudio.fitbond.repository.ZegoCallManager;
import com.amstudio.fitbond.service.CallActionReceiver;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TRTCCallActivity extends AppCompatActivity {

    private static final String TAG = "TRTCCallActivity";

    public static final String EXTRA_CALL_ID = "extra_call_id";
    public static final String EXTRA_TARGET_UID = "extra_target_uid";
    public static final String EXTRA_USER_NAME = "extra_user_name";
    public static final String EXTRA_USER_INITIALS = "extra_user_initials";
    public static final String EXTRA_AVATAR_URL = "extra_avatar_url";
    public static final String EXTRA_IS_VIDEO = "extra_is_video";
    public static final String EXTRA_IS_INCOMING = "extra_is_incoming";
    public static final String EXTRA_AUTO_ACCEPT = "extra_auto_accept";

    // Audio Call / Ringing Views
    private View layoutCallTopBar;
    private View viewAvatarAmbientGlow;
    private View layoutCallProfile;
    private ImageView ivCallAvatar;
    private TextView tvAvatarInitials;
    private TextView tvUserName;
    private TextView tvStatus;
    private TextView tvTimer;

    // Outgoing / Active Audio Buttons
    private ImageButton btnCallBack;
    private ImageButton btnMute;
    private ImageButton btnCamera;
    private ImageButton btnSpeaker;
    private ImageButton btnKeypad;
    private ImageButton btnEnd;
    private View layoutEndCallContainer;

    // Incoming Call Buttons
    private ImageButton btnIncomingRemind;
    private ImageButton btnIncomingMessage;
    private ImageButton btnAccept;
    private ImageButton btnDecline;
    private View layoutDeclineContainer;
    private View layoutAcceptContainer;

    // Active Connected Video Call Overlay Views
    private View layoutVideoFullContainer;
    private TextureView tvRemoteVideoTexture;
    private ImageView ivRemoteVideoPreview;
    private View layoutVideoTopBar;
    private TextView tvVideoTimerPill;
    private ImageButton btnVideoBack;
    private ImageButton btnVideoExpand;
    private ImageButton btnVideoSwitchCamera;
    private View cardLocalVideoPip;
    private TextureView tvLocalVideoTexture;
    private ImageView ivCallVideoPreview;
    private View layoutVideoBottomBar;

    private ImageButton btnVideoChat;
    private ImageButton btnVideoMic;
    private ImageButton btnVideoEnd;
    private ImageButton btnVideoCamera;
    private ImageButton btnVideoSpeaker;

    private String callId;
    private String targetUid;
    private String userName;
    private String userInitials;
    private String avatarUrl;
    private boolean isVideo;
    private boolean isIncoming;
    private boolean shouldAutoAccept = false;
    private boolean isAcceptingCall = false;

    private boolean isMuted = false;
    private boolean isSpeakerOn = true;
    private boolean isCameraOn = true;
    private boolean isCallConnected = false;
    private boolean isFrontCamera = true;

    private boolean isZegoRoomEntered = false;
    private boolean isRemoteCameraOpen = true;

    private BottomSheetDialog videoRequestDialog;

    private ListenerRegistration callListener;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private long callStartTimeMs = 0;
    private long callConnectedAtMs = 0;

    private Ringtone ringtonePlayer;
    private CountDownTimer timeoutTimer;

    private void startRingtone() {
        try {
            if (ringtonePlayer == null) {
                Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                ringtonePlayer = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ringtonePlayer != null) {
                    ringtonePlayer.setLooping(true);
                }
            }
            if (ringtonePlayer != null && !ringtonePlayer.isPlaying()) {
                ringtonePlayer.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ringtone: " + e.getMessage());
        }
    }

    private void stopRingtone() {
        try {
            if (ringtonePlayer != null && ringtonePlayer.isPlaying()) {
                ringtonePlayer.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop ringtone: " + e.getMessage());
        }
    }

    private void start60SecTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }

        timeoutTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                if (!isCallConnected && !isFinishing()) {
                    if (tvStatus != null) {
                        tvStatus.setVisibility(View.VISIBLE);
                        String statusText = isIncoming ? "Incoming Call (" + seconds + "s)" : "Calling... (" + seconds + "s)";
                        tvStatus.setText(statusText);
                    }
                }
            }

            @Override
            public void onFinish() {
                if (!isCallConnected && !isFinishing()) {
                    Log.d(TAG, "60-second call pickup timeout reached. Ending call.");
                    stopRingtone();
                    Toast.makeText(TRTCCallActivity.this, "Call Unanswered", Toast.LENGTH_SHORT).show();

                    if (!isIncoming && targetUid != null && !targetUid.isEmpty()) {
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        String callerName = currentUser != null && currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "FitBond User";
                        String callerUid = currentUser != null ? currentUser.getUid() : "";
                        String callerAvatar = currentUser != null && currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "";
                        NotificationHelper.showMissedCallNotification(
                                getApplicationContext(), callId, targetUid, userName, avatarUrl, isVideo, System.currentTimeMillis()
                        );
                        OneSignalManager.getInstance().sendMissedCallNotification(targetUid, callerName, callerUid, callerAvatar, callId, isVideo);
                    } else if (isIncoming) {
                        NotificationHelper.showMissedCallNotification(
                                getApplicationContext(), callId, targetUid, userName, avatarUrl, isVideo, System.currentTimeMillis()
                        );
                    }

                    endOrRejectCall();
                }
            }
        }.start();
    }

    private ActivityResultLauncher<String[]> permissionLauncher;

    public static void start(Context context, String callId, String targetUid, String userName,
                             String userInitials, boolean isVideo, boolean isIncoming) {
        start(context, callId, targetUid, userName, userInitials, null, isVideo, isIncoming);
    }

    public static void start(Context context, String callId, String targetUid, String userName,
                             String userInitials, String avatarUrl, boolean isVideo, boolean isIncoming) {
        if (context == null) return;
        Intent intent = new Intent(context, TRTCCallActivity.class);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_TARGET_UID, targetUid);
        intent.putExtra(EXTRA_USER_NAME, userName);
        intent.putExtra(EXTRA_USER_INITIALS, userInitials);
        intent.putExtra(EXTRA_AVATAR_URL, avatarUrl);
        intent.putExtra(EXTRA_IS_VIDEO, isVideo);
        intent.putExtra(EXTRA_IS_INCOMING, isIncoming);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cancel incoming call notification when call activity opens
        NotificationHelper.cancelCallNotification(this);

        // Configure window for lockscreen display & transparent edge-to-edge system bars
        Window window = getWindow();
        if (window != null) {
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.dialog_trtc_call);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean audioGranted = result.get(Manifest.permission.RECORD_AUDIO);
                    if (Boolean.TRUE.equals(audioGranted)) {
                        Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show();
                        setupAudioHardwareForCall();
                        if (!isIncoming && !isZegoRoomEntered) {
                            initAndEnterZegoRoom();
                        }
                    } else {
                        Toast.makeText(this, "Audio permission required for calling", Toast.LENGTH_LONG).show();
                    }
                }
        );

        initViews();
        setupWindowInsets();
        parseIntentExtras();
        bindCallUI();
        setupListeners();
        checkAndRequestCallPermissions();
        observeCallStatusInFirestore();
        fetchTargetProfileIfNeeded();
        loadCurrentUserAvatarForPip();

        boolean isRoomAlreadyJoined = ZegoCallManager.getInstance().getCurrentRoomId() != null &&
                ZegoCallManager.getInstance().getCurrentRoomId().equals(callId);
        long storedConnectedAt = ActiveCallManager.getInstance().getConnectedAtMs();

        if (isRoomAlreadyJoined && storedConnectedAt > 0) {
            isCallConnected = true;
            callConnectedAtMs = storedConnectedAt;
            isZegoRoomEntered = true;
            ZegoCallManager.getInstance().setLocalVideoView(tvLocalVideoTexture);
            ZegoCallManager.getInstance().setRemoteVideoView(tvRemoteVideoTexture);
            bindCallUI();
            startCallTimer();
        } else if (shouldAutoAccept) {
            acceptCall();
        } else {
            bindCallUI();
            start60SecTimeoutTimer();
            if (isIncoming) {
                startRingtone();
            }
            if (!isIncoming) {
                boolean audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                if (audioGranted) {
                    initAndEnterZegoRoom();
                }
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                minimizeCall();
            }
        });
    }

    private void checkAndRequestCallPermissions() {
        boolean audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean cameraGranted = !isVideo || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (!audioGranted || !cameraGranted) {
            List<String> needed = new ArrayList<>();
            if (!audioGranted) needed.add(Manifest.permission.RECORD_AUDIO);
            if (!cameraGranted) needed.add(Manifest.permission.CAMERA);

            if (permissionLauncher != null) {
                permissionLauncher.launch(needed.toArray(new String[0]));
            }
        }
    }

    private void initViews() {
        layoutCallTopBar = findViewById(R.id.layout_call_top_bar);
        viewAvatarAmbientGlow = findViewById(R.id.view_avatar_ambient_glow);
        layoutCallProfile = findViewById(R.id.layout_call_profile);
        ivCallAvatar = findViewById(R.id.iv_call_avatar);
        tvAvatarInitials = findViewById(R.id.tv_call_avatar_initials);
        tvUserName = findViewById(R.id.tv_call_user_name);
        tvStatus = findViewById(R.id.tv_call_status);
        tvTimer = findViewById(R.id.tv_call_timer);

        btnCallBack = findViewById(R.id.btn_call_back);
        btnMute = findViewById(R.id.btn_call_mute);
        btnCamera = findViewById(R.id.btn_call_camera);
        btnSpeaker = findViewById(R.id.btn_call_speaker);
        btnEnd = findViewById(R.id.btn_call_end);
        layoutEndCallContainer = findViewById(R.id.layout_end_call_container);

        btnIncomingRemind = findViewById(R.id.btn_incoming_remind);
        btnIncomingMessage = findViewById(R.id.btn_incoming_message);
        btnAccept = findViewById(R.id.btn_call_accept);
        btnDecline = findViewById(R.id.btn_call_decline);
        layoutDeclineContainer = findViewById(R.id.layout_decline_container);
        layoutAcceptContainer = findViewById(R.id.layout_accept_container);

        // Connected Video Call Overlay Views
        layoutVideoFullContainer = findViewById(R.id.layout_video_full_container);
        tvRemoteVideoTexture = findViewById(R.id.tv_remote_video_texture);
        ivRemoteVideoPreview = findViewById(R.id.iv_remote_video_preview);
        layoutVideoTopBar = findViewById(R.id.layout_video_top_bar);
        tvVideoTimerPill = findViewById(R.id.tv_video_timer_pill);
        btnVideoBack = findViewById(R.id.btn_video_back);
        btnVideoExpand = findViewById(R.id.btn_video_expand);
        btnVideoSwitchCamera = findViewById(R.id.btn_video_switch_camera);
        cardLocalVideoPip = findViewById(R.id.card_local_video_pip);
        tvLocalVideoTexture = findViewById(R.id.tv_local_video_texture);
        ivCallVideoPreview = findViewById(R.id.iv_call_video_preview);
        layoutVideoBottomBar = findViewById(R.id.layout_video_bottom_bar);

        btnVideoChat = findViewById(R.id.btn_video_chat);
        btnVideoMic = findViewById(R.id.btn_video_mic);
        btnVideoEnd = findViewById(R.id.btn_video_end);
        btnVideoCamera = findViewById(R.id.btn_video_camera);
        btnVideoSpeaker = findViewById(R.id.btn_video_speaker);
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.trtc_call_root);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

                if (statusBarHeight <= 0) {
                    statusBarHeight = dpToPx(24);
                }

                if (layoutVideoTopBar != null) {
                    layoutVideoTopBar.setPadding(
                            dpToPx(16),
                            statusBarHeight + dpToPx(8),
                            dpToPx(16),
                            dpToPx(12)
                    );
                }

                if (layoutCallTopBar != null) {
                    ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) layoutCallTopBar.getLayoutParams();
                    if (lp != null) {
                        lp.topMargin = statusBarHeight + dpToPx(16);
                        layoutCallTopBar.setLayoutParams(lp);
                    }
                }

                if (btnCallBack != null) {
                    ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) btnCallBack.getLayoutParams();
                    if (lp != null) {
                        lp.topMargin = statusBarHeight + dpToPx(12);
                        btnCallBack.setLayoutParams(lp);
                    }
                }

                if (layoutVideoBottomBar != null) {
                    ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) layoutVideoBottomBar.getLayoutParams();
                    if (lp != null) {
                        lp.bottomMargin = navBarHeight + dpToPx(24);
                        layoutVideoBottomBar.setLayoutParams(lp);
                    }
                }

                View anchor = findViewById(R.id.view_call_controls_anchor);
                if (anchor != null) {
                    ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) anchor.getLayoutParams();
                    if (lp != null) {
                        lp.bottomMargin = navBarHeight + dpToPx(84);
                        anchor.setLayoutParams(lp);
                    }
                }

                return insets;
            });
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        parseIntentExtras();
        if (shouldAutoAccept && !isCallConnected) {
            acceptCall();
        }
        if (ActiveCallManager.getInstance().isActive()) {
            ZegoCallManager.getInstance().setLocalVideoView(tvLocalVideoTexture);
            ZegoCallManager.getInstance().setRemoteVideoView(tvRemoteVideoTexture);
            bindCallUI();
            if (ActiveCallManager.getInstance().getCallState() == ActiveCallManager.CallState.CONNECTED) {
                startCallTimer();
            }
        }
    }

    private void parseIntentExtras() {
        Intent intent = getIntent();
        if (intent != null) {
            callId = intent.getStringExtra(EXTRA_CALL_ID);
            if (callId == null) callId = "call_" + System.currentTimeMillis();
            targetUid = intent.getStringExtra(EXTRA_TARGET_UID);
            if (targetUid == null) targetUid = "";
            userName = intent.getStringExtra(EXTRA_USER_NAME);
            if (userName == null) userName = "Gym Buddy";
            userInitials = intent.getStringExtra(EXTRA_USER_INITIALS);
            if (userInitials == null) userInitials = "GB";
            avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL);
            isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false);
            isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false);
            shouldAutoAccept = intent.getBooleanExtra(EXTRA_AUTO_ACCEPT, false)
                    || CallActionReceiver.ACTION_ACCEPT_CALL.equals(intent.getAction());
            isSpeakerOn = isVideo;
            isCameraOn = isVideo;
        }
    }

    private void bindCallUI() {
        if (tvUserName != null) tvUserName.setText(userName);
        if (tvAvatarInitials != null) tvAvatarInitials.setText(userInitials);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            loadAvatarImage(avatarUrl);
        }

        if (isVideo && isCallConnected) {
            showActiveVideoCallUI();
        } else {
            showAudioOrRingingCallUI();
        }

        updateControlsState();
    }

    private void showActiveVideoCallUI() {
        if (layoutVideoFullContainer != null) layoutVideoFullContainer.setVisibility(View.VISIBLE);
        if (layoutVideoTopBar != null) {
            layoutVideoTopBar.setVisibility(View.VISIBLE);
            View root = findViewById(R.id.trtc_call_root);
            if (root != null) {
                ViewCompat.requestApplyInsets(root);
            }
        }
        if (cardLocalVideoPip != null) cardLocalVideoPip.setVisibility(View.VISIBLE);
        if (tvLocalVideoTexture != null) tvLocalVideoTexture.setVisibility(isCameraOn ? View.VISIBLE : View.GONE);
        if (ivCallVideoPreview != null) ivCallVideoPreview.setVisibility(isCameraOn ? View.GONE : View.VISIBLE);

        if (isRemoteCameraOpen) {
            if (tvRemoteVideoTexture != null) tvRemoteVideoTexture.setVisibility(View.VISIBLE);
            if (ivRemoteVideoPreview != null) ivRemoteVideoPreview.setVisibility(View.GONE);
        } else {
            if (tvRemoteVideoTexture != null) tvRemoteVideoTexture.setVisibility(View.GONE);
            if (ivRemoteVideoPreview != null) {
                ivRemoteVideoPreview.setVisibility(View.VISIBLE);
                ivRemoteVideoPreview.bringToFront();
            }
        }

        if (layoutVideoBottomBar != null) layoutVideoBottomBar.setVisibility(View.VISIBLE);

        ZegoCallManager.getInstance().setLocalVideoView(tvLocalVideoTexture);

        // Hide Audio Call / Calling screen
        if (btnCallBack != null) btnCallBack.setVisibility(View.GONE);
        if (layoutCallTopBar != null) layoutCallTopBar.setVisibility(View.GONE);
        if (layoutCallProfile != null) layoutCallProfile.setVisibility(View.GONE);
        if (viewAvatarAmbientGlow != null) viewAvatarAmbientGlow.setVisibility(View.GONE);
        if (btnCamera != null) btnCamera.setVisibility(View.GONE);
        if (btnMute != null) btnMute.setVisibility(View.GONE);
        if (btnSpeaker != null) btnSpeaker.setVisibility(View.GONE);
        if (btnKeypad != null) btnKeypad.setVisibility(View.GONE);
        if (layoutEndCallContainer != null) layoutEndCallContainer.setVisibility(View.GONE);

        if (btnIncomingRemind != null) btnIncomingRemind.setVisibility(View.GONE);
        if (btnIncomingMessage != null) btnIncomingMessage.setVisibility(View.GONE);
        if (layoutDeclineContainer != null) layoutDeclineContainer.setVisibility(View.GONE);
        if (layoutAcceptContainer != null) layoutAcceptContainer.setVisibility(View.GONE);
    }

    private void showAudioOrRingingCallUI() {
        if (layoutVideoFullContainer != null) layoutVideoFullContainer.setVisibility(View.GONE);
        if (layoutVideoTopBar != null) layoutVideoTopBar.setVisibility(View.GONE);
        if (cardLocalVideoPip != null) cardLocalVideoPip.setVisibility(View.GONE);
        if (layoutVideoBottomBar != null) layoutVideoBottomBar.setVisibility(View.GONE);

        if (btnCallBack != null) {
            btnCallBack.setVisibility(View.VISIBLE);
            btnCallBack.bringToFront();
        }
        if (layoutCallTopBar != null) layoutCallTopBar.setVisibility(View.VISIBLE);
        if (layoutCallProfile != null) layoutCallProfile.setVisibility(View.VISIBLE);
        if (viewAvatarAmbientGlow != null) viewAvatarAmbientGlow.setVisibility(View.VISIBLE);

        if (isIncoming && !isCallConnected) {
            if (tvStatus != null) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("Incoming Call");
            }
            if (tvTimer != null) tvTimer.setVisibility(View.GONE);

            if (btnIncomingRemind != null) btnIncomingRemind.setVisibility(View.VISIBLE);
            if (btnIncomingMessage != null) btnIncomingMessage.setVisibility(View.VISIBLE);
            if (layoutDeclineContainer != null) layoutDeclineContainer.setVisibility(View.VISIBLE);
            if (layoutAcceptContainer != null) layoutAcceptContainer.setVisibility(View.VISIBLE);

            if (layoutEndCallContainer != null) layoutEndCallContainer.setVisibility(View.GONE);
            if (btnCamera != null) btnCamera.setVisibility(View.GONE);
            if (btnMute != null) btnMute.setVisibility(View.GONE);
            if (btnSpeaker != null) btnSpeaker.setVisibility(View.GONE);
            if (btnKeypad != null) btnKeypad.setVisibility(View.GONE);
        } else {
            if (tvStatus != null) {
                if (isCallConnected) {
                    tvStatus.setVisibility(View.GONE);
                } else {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("Connecting");
                }
            }
            if (tvTimer != null) {
                tvTimer.setVisibility(isCallConnected ? View.VISIBLE : View.GONE);
            }

            if (btnIncomingRemind != null) btnIncomingRemind.setVisibility(View.GONE);
            if (btnIncomingMessage != null) btnIncomingMessage.setVisibility(View.GONE);
            if (layoutDeclineContainer != null) layoutDeclineContainer.setVisibility(View.GONE);
            if (layoutAcceptContainer != null) layoutAcceptContainer.setVisibility(View.GONE);

            if (layoutEndCallContainer != null) layoutEndCallContainer.setVisibility(View.VISIBLE);
            if (btnCamera != null) btnCamera.setVisibility(View.VISIBLE);
            if (btnMute != null) btnMute.setVisibility(View.VISIBLE);
            if (btnSpeaker != null) btnSpeaker.setVisibility(View.VISIBLE);
            if (btnKeypad != null) btnKeypad.setVisibility(View.VISIBLE);
        }
    }

    private void fetchTargetProfileIfNeeded() {
        if (targetUid == null || targetUid.isEmpty()) {
            return;
        }

        boolean needName = userName == null || userName.isEmpty() || "Gym Buddy".equalsIgnoreCase(userName);
        boolean needAvatar = avatarUrl == null || avatarUrl.isEmpty();

        if (!needName && !needAvatar) {
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(targetUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists() && !isFinishing()) {
                        String photo = snapshot.getString("avatarUrl");
                        if (photo == null || photo.isEmpty()) {
                            photo = snapshot.getString("photoUrl");
                        }
                        String fetchedName = snapshot.getString("displayName");
                        if (fetchedName == null || fetchedName.isEmpty()) {
                            fetchedName = snapshot.getString("fullName");
                        }
                        if (fetchedName != null && !fetchedName.isEmpty()) {
                            userName = fetchedName;
                            if (tvUserName != null) {
                                tvUserName.setText(userName);
                            }
                        }

                        if (photo != null && !photo.isEmpty()) {
                            avatarUrl = photo;
                            loadAvatarImage(avatarUrl);
                        }
                    }
                });
    }

    private void loadAvatarImage(String url) {
        if (url == null || url.trim().isEmpty() || isFinishing()) return;

        if (ivCallAvatar != null) {
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .into(ivCallAvatar);
            ivCallAvatar.setVisibility(View.VISIBLE);
        }

        if (ivRemoteVideoPreview != null) {
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .into(ivRemoteVideoPreview);
        }

        if (tvAvatarInitials != null) {
            tvAvatarInitials.setVisibility(View.GONE);
        }
    }

    private void loadCurrentUserAvatarForPip() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid != null && !currentUid.isEmpty()) {
            UserRepository.getInstance().getUserProfile(currentUid, new UserRepository.ProfileCallback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    if (profile != null && !isFinishing()) {
                        String myAvatar = profile.getAvatarUrl();
                        if (myAvatar != null && !myAvatar.isEmpty() && ivCallVideoPreview != null) {
                            Glide.with(TRTCCallActivity.this)
                                    .load(myAvatar)
                                    .placeholder(R.drawable.ic_avatar_placeholder)
                                    .error(R.drawable.ic_avatar_placeholder)
                                    .into(ivCallVideoPreview);
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                }
            });
        }
    }

    private void updateControlsState() {
        if (btnMute != null) {
            btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
            btnMute.setBackgroundResource(isMuted ? R.drawable.bg_call_satellite_btn_active : R.drawable.bg_call_satellite_btn);
        }

        if (btnSpeaker != null) {
            btnSpeaker.setImageResource(isSpeakerOn ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
            btnSpeaker.setBackgroundResource(isSpeakerOn ? R.drawable.bg_call_satellite_btn_active : R.drawable.bg_call_satellite_btn);
        }

        if (btnCamera != null) {
            btnCamera.setBackgroundResource(isCameraOn ? R.drawable.bg_call_satellite_btn_active : R.drawable.bg_call_satellite_btn);
        }

        if (btnVideoMic != null) {
            btnVideoMic.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
        }

        if (btnVideoCamera != null) {
            btnVideoCamera.setImageResource(isCameraOn ? R.drawable.ic_video_call : R.drawable.ic_video_off);
        }

        if (btnVideoSpeaker != null) {
            btnVideoSpeaker.setImageResource(isSpeakerOn ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
        }

        if (cardLocalVideoPip != null) {
            boolean showPip = (isVideo && isCallConnected);
            cardLocalVideoPip.setVisibility(showPip ? View.VISIBLE : View.GONE);
            if (tvLocalVideoTexture != null) {
                tvLocalVideoTexture.setVisibility(isCameraOn ? View.VISIBLE : View.GONE);
            }
            if (ivCallVideoPreview != null) {
                ivCallVideoPreview.setVisibility(isCameraOn ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void setupListeners() {
        if (btnCallBack != null) {
            btnCallBack.setOnClickListener(v -> minimizeCall());
        }

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> acceptCall());
        }

        if (btnEnd != null) {
            btnEnd.setOnClickListener(v -> endOrRejectCall());
        }

        if (btnDecline != null) {
            btnDecline.setOnClickListener(v -> endOrRejectCall());
        }

        if (btnIncomingRemind != null) {
            btnIncomingRemind.setOnClickListener(v -> handleRemindMe());
        }

        if (btnMute != null) {
            btnMute.setOnClickListener(v -> toggleMute());
        }

        if (btnSpeaker != null) {
            btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        }

        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> toggleCamera());
        }

        if (btnKeypad != null) {
            btnKeypad.setOnClickListener(v -> showKeypadDialog());
        }

        if (btnVideoEnd != null) {
            btnVideoEnd.setOnClickListener(v -> endOrRejectCall());
        }

        if (btnVideoMic != null) {
            btnVideoMic.setOnClickListener(v -> toggleMute());
        }

        if (btnVideoCamera != null) {
            btnVideoCamera.setOnClickListener(v -> toggleCamera());
        }

        if (btnVideoSpeaker != null) {
            btnVideoSpeaker.setOnClickListener(v -> toggleSpeaker());
        }

        if (btnVideoChat != null) {
            btnVideoChat.setOnClickListener(v -> openChatScreen());
        }

        if (btnVideoBack != null) {
            btnVideoBack.setOnClickListener(v -> minimizeCall());
        }

        if (btnVideoSwitchCamera != null) {
            btnVideoSwitchCamera.setOnClickListener(v -> switchCamera());
        }

        if (btnVideoExpand != null) {
            btnVideoExpand.setOnClickListener(v -> toggleExpandMode());
        }
    }

    private void openChatScreen() {
        if (targetUid != null && !targetUid.isEmpty()) {
            Intent intent = new Intent(this, IndividualChatActivity.class);
            intent.putExtra(IndividualChatActivity.EXTRA_CHAT_ID, targetUid);
            intent.putExtra(IndividualChatActivity.EXTRA_CHAT_NAME, userName);
            intent.putExtra(IndividualChatActivity.EXTRA_CHAT_INITIALS, userInitials);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Chat unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleRemindMe() {
        Toast.makeText(this, "Reminder set for 1 hour", Toast.LENGTH_SHORT).show();
        endOrRejectCall();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        setHardwareMute(isMuted);
        updateControlsState();
        Toast.makeText(this, isMuted ? "Microphone Muted" : "Microphone Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        setHardwareSpeaker(isSpeakerOn);
        updateControlsState();
        Toast.makeText(this, isSpeakerOn ? "Speakerphone On (High Volume)" : "Normal Earpiece Mode", Toast.LENGTH_SHORT).show();
    }

    private void toggleCamera() {
        if (isVideo) {
            isCameraOn = !isCameraOn;
            ZegoCallManager.getInstance().enableCamera(isCameraOn);
            updateControlsState();
            Toast.makeText(this, isCameraOn ? "Camera On" : "Camera Off", Toast.LENGTH_SHORT).show();
        } else {
            String currentUid = UserRepository.getInstance().getCurrentUid();
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String myName = (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty())
                    ? currentUser.getDisplayName() : "User";

            Map<String, Object> updates = new HashMap<>();
            updates.put("videoRequestStatus", "PENDING");
            updates.put("videoRequestFrom", currentUid != null ? currentUid : "");
            updates.put("videoRequestFromName", myName);
            FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates);

            Toast.makeText(this, "Video call request sent to " + userName + "...", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchCamera() {
        isFrontCamera = !isFrontCamera;
        ZegoCallManager.getInstance().switchCamera(isFrontCamera);
        Toast.makeText(this, isFrontCamera ? "Front Camera" : "Rear Camera", Toast.LENGTH_SHORT).show();
    }

    private void toggleExpandMode() {
        Toast.makeText(this, "Aspect Ratio Changed", Toast.LENGTH_SHORT).show();
    }

    private void minimizeCall() {
        Toast.makeText(this, "Call minimized to background", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private void showKeypadDialog() {
        BottomSheetDialog keypadSheet = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_call_keypad, null, false);
        keypadSheet.setContentView(dialogView);

        TextView tvKeypadDigits = dialogView.findViewById(R.id.tv_keypad_digits);
        View btnClose = dialogView.findViewById(R.id.btn_keypad_close);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> keypadSheet.dismiss());
        }

        int[] numButtonIds = new int[]{
                R.id.btn_num_0, R.id.btn_num_1, R.id.btn_num_2, R.id.btn_num_3,
                R.id.btn_num_4, R.id.btn_num_5, R.id.btn_num_6, R.id.btn_num_7,
                R.id.btn_num_8, R.id.btn_num_9, R.id.btn_num_star, R.id.btn_num_hash
        };

        View.OnClickListener numListener = v -> {
            if (v instanceof TextView) {
                String digit = ((TextView) v).getText().toString();
                if (tvKeypadDigits != null) {
                    tvKeypadDigits.append(digit);
                }
            }
        };

        for (int id : numButtonIds) {
            View btn = dialogView.findViewById(id);
            if (btn != null) btn.setOnClickListener(numListener);
        }

        keypadSheet.show();
    }

    private void setupAudioHardwareForCall() {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                if (maxVol > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Audio hardware setup warning: " + e.getLocalizedMessage());
        }
        ZegoCallManager.getInstance().setMute(isMuted);
        ZegoCallManager.getInstance().setSpeaker(isSpeakerOn);
    }

    private void setHardwareMute(boolean mute) {
        ZegoCallManager.getInstance().setMute(mute);
    }

    private void setHardwareSpeaker(boolean speaker) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(speaker);
                int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                if (speaker) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0);
                } else {
                    int normalVol = Math.max(1, maxVol / 2);
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, normalVol, 0);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Audio hardware speaker error: " + e.getLocalizedMessage());
        }
        ZegoCallManager.getInstance().setSpeaker(speaker);
    }

    private void resetAudioHardwareAfterCall() {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMicrophoneMute(false);
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Exception e) {
            Log.w(TAG, "Audio hardware reset warning: " + e.getLocalizedMessage());
        }
    }

    private void observeCallStatusInFirestore() {
        if (callId == null || callId.isEmpty()) return;

        callListener = FirebaseFirestore.getInstance().collection("calls").document(callId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists() || isFinishing()) return;

                    String status = doc.getString("status");
                    if ("CONNECTED".equalsIgnoreCase(status)) {
                        Timestamp connTs = doc.getTimestamp("connectedAt");
                        if (connTs != null && callConnectedAtMs <= 0) {
                            callConnectedAtMs = connTs.toDate().getTime();
                        }
                        onCallConnected();
                    } else if ("ACCEPTED".equalsIgnoreCase(status)) {
                        if (!isCallConnected) {
                            if (tvStatus != null) {
                                tvStatus.setVisibility(View.VISIBLE);
                                tvStatus.setText("Connecting...");
                            }
                        }
                    } else if ("REJECTED".equalsIgnoreCase(status) || "ENDED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status) || "MISSED".equalsIgnoreCase(status)) {
                        stopRingtone();
                        NotificationHelper.cancelCallNotification(TRTCCallActivity.this, callId);
                        NotificationHelper.cancelActiveCallNotification(TRTCCallActivity.this);

                        if (("REJECTED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status) || "MISSED".equalsIgnoreCase(status))
                                && !isIncoming && !isCallConnected) {
                            NotificationHelper.showMissedCallNotification(
                                    getApplicationContext(), callId, targetUid, userName, avatarUrl, isVideo, System.currentTimeMillis()
                            );
                        } else if ("CANCELLED".equalsIgnoreCase(status) && isIncoming && !isCallConnected) {
                            NotificationHelper.showMissedCallNotification(
                                    getApplicationContext(), callId, targetUid, userName, avatarUrl, isVideo, System.currentTimeMillis()
                            );
                        }

                        Toast.makeText(TRTCCallActivity.this, "Call " + (status != null ? status.toLowerCase() : "ended"), Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String videoReqStatus = doc.getString("videoRequestStatus");
                    String videoReqFrom = doc.getString("videoRequestFrom");
                    String videoReqFromName = doc.getString("videoRequestFromName");
                    String currentUid = UserRepository.getInstance().getCurrentUid();

                    if ("PENDING".equalsIgnoreCase(videoReqStatus)) {
                        if (videoReqFrom != null && !videoReqFrom.equals(currentUid)) {
                            String reqName = (videoReqFromName != null && !videoReqFromName.isEmpty() && !"Gym Buddy".equalsIgnoreCase(videoReqFromName))
                                    ? videoReqFromName : (userName != null && !"Gym Buddy".equalsIgnoreCase(userName) ? userName : "User");
                            showVideoRequestDialog(reqName);
                        }
                    } else if ("ACCEPTED".equalsIgnoreCase(videoReqStatus)) {
                        if (videoRequestDialog != null && videoRequestDialog.isShowing()) {
                            videoRequestDialog.dismiss();
                        }
                        if (!isVideo) {
                            isVideo = true;
                            isCameraOn = true;
                            isSpeakerOn = true;
                            setHardwareSpeaker(true);
                            ZegoCallManager.getInstance().enableCamera(true);
                            bindCallUI();
                            Toast.makeText(this, "Switched to Video Call!", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("DECLINED".equalsIgnoreCase(videoReqStatus)) {
                        if (videoRequestDialog != null && videoRequestDialog.isShowing()) {
                            videoRequestDialog.dismiss();
                        }
                        if (videoReqFrom != null && videoReqFrom.equals(currentUid)) {
                            Toast.makeText(this, userName + " declined video call request", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void showVideoRequestDialog(String requestorName) {
        if ((videoRequestDialog != null && videoRequestDialog.isShowing()) || isFinishing()) return;

        String displayName = (requestorName != null && !requestorName.isEmpty() && !"Gym Buddy".equalsIgnoreCase(requestorName))
                ? requestorName : (userName != null && !"Gym Buddy".equalsIgnoreCase(userName) ? userName : "User");

        videoRequestDialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_video_call_request, null, false);
        videoRequestDialog.setContentView(dialogView);
        videoRequestDialog.setCancelable(false);

        TextView tvMsg = dialogView.findViewById(R.id.tv_video_dialog_message);
        if (tvMsg != null) {
            tvMsg.setText(displayName + " wants to switch to Video Call.");
        }

        View btnAccept = dialogView.findViewById(R.id.btn_dialog_accept);
        View btnDecline = dialogView.findViewById(R.id.btn_dialog_decline);

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                if (videoRequestDialog != null) videoRequestDialog.dismiss();
                Map<String, Object> updates = new HashMap<>();
                updates.put("videoRequestStatus", "ACCEPTED");
                updates.put("isVideo", true);
                FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates);

                isVideo = true;
                isCameraOn = true;
                isSpeakerOn = true;
                setHardwareSpeaker(true);
                ZegoCallManager.getInstance().enableCamera(true);
                bindCallUI();
            });
        }

        if (btnDecline != null) {
            btnDecline.setOnClickListener(v -> {
                if (videoRequestDialog != null) videoRequestDialog.dismiss();
                Map<String, Object> updates = new HashMap<>();
                updates.put("videoRequestStatus", "DECLINED");
                FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates);
            });
        }

        videoRequestDialog.show();
    }

    private synchronized void acceptCall() {
        if (isCallConnected || isAcceptingCall) {
            Log.d(TAG, "acceptCall ignored: already connected or accepting");
            return;
        }
        isAcceptingCall = true;

        Log.d(TAG, "acceptCall started for callId: " + callId);
        stopRingtone();
        NotificationHelper.cancelCallNotification(this, callId);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "acceptCall failed: User not authenticated");
            Toast.makeText(this, "Please sign in to answer call", Toast.LENGTH_SHORT).show();
            isAcceptingCall = false;
            finish();
            return;
        }

        if (callId == null || callId.isEmpty()) {
            Log.e(TAG, "acceptCall failed: Invalid Call ID");
            Toast.makeText(this, "Invalid Call Session", Toast.LENGTH_SHORT).show();
            isAcceptingCall = false;
            finish();
            return;
        }

        FirebaseFirestore.getInstance().collection("calls").document(callId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String status = doc.getString("status");
                        Log.d(TAG, "Firestore call status for acceptCall: " + status);

                        if ("CALLING".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status)) {
                            if ("CALLING".equalsIgnoreCase(status)) {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("status", "ACCEPTED");
                                updates.put("acceptedAt", FieldValue.serverTimestamp());

                                FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates)
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore call status updated to ACCEPTED"))
                                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update Firestore status to ACCEPTED: " + e.getMessage()));

                                if (targetUid != null && !targetUid.isEmpty()) {
                                    TencentChatManager.getInstance().sendTrtcCallSignalMessage(
                                            targetUid, callId, isVideo ? "VIDEO" : "VOICE", "ACCEPT", null
                                    );
                                }
                            }

                            isIncoming = false;
                            isAcceptingCall = false;
                            setupAudioHardwareForCall();
                            initAndEnterZegoRoom();
                        } else {
                            Log.w(TAG, "Call no longer available: status=" + status);
                            Toast.makeText(TRTCCallActivity.this, "Call no longer available (" + (status != null ? status.toLowerCase() : "ended") + ")", Toast.LENGTH_SHORT).show();
                            isAcceptingCall = false;
                            finish();
                        }
                    } else {
                        Log.w(TAG, "Call document does not exist in Firestore for callId: " + callId);
                        Toast.makeText(TRTCCallActivity.this, "Call has expired or ended", Toast.LENGTH_SHORT).show();
                        isAcceptingCall = false;
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking call in Firestore: " + e.getMessage(), e);
                    isIncoming = false;
                    isAcceptingCall = false;
                    setupAudioHardwareForCall();
                    initAndEnterZegoRoom();
                });
    }

    private void onCallConnected() {
        if (isCallConnected || isFinishing()) return;
        isCallConnected = true;
        Log.d(TAG, "onCallConnected triggered for callId: " + callId);

        stopRingtone();
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }

        setupAudioHardwareForCall();

        boolean audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (audioGranted) {
            initAndEnterZegoRoom();
        } else {
            checkAndRequestCallPermissions();
        }

        if (callConnectedAtMs <= 0) {
            callConnectedAtMs = System.currentTimeMillis();
        }
        ActiveCallManager.getInstance().setCallConnected(callConnectedAtMs);

        if (callId != null && !callId.isEmpty()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "CONNECTED");
            updates.put("connectedAt", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates);
        }

        CallForegroundService.startService(
                this, callId, targetUid, userName, avatarUrl, isVideo, isMuted, isSpeakerOn
        );

        bindCallUI();
        startCallTimer();
    }

    private void initAndEnterZegoRoom() {
        if (isZegoRoomEntered || isFinishing()) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null || currentUid.isEmpty()) {
            currentUid = "user_" + System.currentTimeMillis();
        }

        ZegoCallManager zegoManager = ZegoCallManager.getInstance();
        zegoManager.initSDK(getApplication());
        zegoManager.setSdkConnectionListener(new ZegoCallManager.SdkConnectionListener() {
            @Override
            public void onSdkConnected() {
                runOnUiThread(() -> {
                    if (!isCallConnected) {
                        onCallConnected();
                    }
                });
            }

            @Override
            public void onSdkDisconnected() {
            }
        });

        zegoManager.setEventListener(new ZegoCallManager.CallEventListener() {
            @Override
            public void onRemoteUserJoined(String streamId) {
                runOnUiThread(() -> {
                    if (!isCallConnected) {
                        onCallConnected();
                    }
                    if (isRemoteCameraOpen && isVideo) {
                        if (ivRemoteVideoPreview != null) ivRemoteVideoPreview.setVisibility(View.GONE);
                        if (tvRemoteVideoTexture != null) tvRemoteVideoTexture.setVisibility(View.VISIBLE);
                    } else {
                        if (ivRemoteVideoPreview != null) ivRemoteVideoPreview.setVisibility(View.VISIBLE);
                        if (tvRemoteVideoTexture != null) tvRemoteVideoTexture.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onRemoteUserLeft(String streamId) {
                runOnUiThread(() -> {
                    if (ivRemoteVideoPreview != null) ivRemoteVideoPreview.setVisibility(View.VISIBLE);
                    if (tvRemoteVideoTexture != null) tvRemoteVideoTexture.setVisibility(View.GONE);
                });
            }

            @Override
            public void onRemoteCameraStateChanged(String streamId, boolean isOpen) {
                runOnUiThread(() -> {
                    isRemoteCameraOpen = isOpen;
                    if (isOpen && isVideo) {
                        if (ivRemoteVideoPreview != null) ivRemoteVideoPreview.setVisibility(View.GONE);
                        if (tvRemoteVideoTexture != null) {
                            tvRemoteVideoTexture.setVisibility(View.VISIBLE);
                            tvRemoteVideoTexture.bringToFront();
                        }
                    } else {
                        if (tvRemoteVideoTexture != null) tvRemoteVideoTexture.setVisibility(View.GONE);
                        if (ivRemoteVideoPreview != null) {
                            ivRemoteVideoPreview.setVisibility(View.VISIBLE);
                            ivRemoteVideoPreview.bringToFront();
                        }
                    }
                    if (layoutVideoTopBar != null) layoutVideoTopBar.bringToFront();
                    if (cardLocalVideoPip != null) cardLocalVideoPip.bringToFront();
                    if (layoutVideoBottomBar != null) layoutVideoBottomBar.bringToFront();
                });
            }

            @Override
            public void onError(int errorCode, String message) {
                Log.e(TAG, "ZEGO Call error (" + errorCode + "): " + message);
            }
        });

        zegoManager.joinRoomAndStartCall(
                getApplication(),
                callId != null ? callId : "call_default",
                currentUid,
                "User",
                isVideo,
                tvLocalVideoTexture,
                tvRemoteVideoTexture
        );
        isZegoRoomEntered = true;
        if (ActiveCallManager.getInstance().getCallState() != ActiveCallManager.CallState.CONNECTED) {
            ActiveCallManager.getInstance().setActiveCall(callId, targetUid, userName, userInitials, avatarUrl, isVideo, callStartTimeMs > 0 ? callStartTimeMs : System.currentTimeMillis(), ActiveCallManager.CallState.CONNECTING);
        }
        Log.d(TAG, "Entered ZEGO room successfully: " + callId);
    }

    private void cleanupZegoRoom() {
        ZegoCallManager.getInstance().leaveRoomAndEndCall();
        isZegoRoomEntered = false;
    }

    private void startCallTimer() {
        if (tvStatus != null) {
            tvStatus.setVisibility(View.GONE);
        }
        if (tvTimer != null) {
            tvTimer.setVisibility(View.VISIBLE);
        }
        if (tvVideoTimerPill != null) {
            tvVideoTimerPill.setVisibility(View.VISIBLE);
        }

        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCallConnected && !isFinishing() && callConnectedAtMs > 0) {
                    long elapsedSec = (System.currentTimeMillis() - callConnectedAtMs) / 1000;
                    if (elapsedSec < 0) elapsedSec = 0;
                    long min = elapsedSec / 60;
                    long sec = elapsedSec % 60;
                    String formatted = String.format(Locale.US, "%02d:%02d", min, sec);
                    if (tvTimer != null) tvTimer.setText(formatted);
                    if (tvVideoTimerPill != null) tvVideoTimerPill.setText(formatted);
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopCallTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    private void endOrRejectCall() {
        stopRingtone();
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
        if (videoRequestDialog != null && videoRequestDialog.isShowing()) {
            videoRequestDialog.dismiss();
        }
        stopCallTimer();
        cleanupZegoRoom();
        resetAudioHardwareAfterCall();

        CallForegroundService.stopService(this);
        NotificationHelper.cancelCallNotification(this, callId);
        NotificationHelper.cancelActiveCallNotification(this);

        String statusStr = isCallConnected ? "ENDED" : (isIncoming ? "REJECTED" : "CANCELLED");

        ActiveCallManager.CallState state;
        if (isCallConnected) {
            state = ActiveCallManager.CallState.CALL_ENDED;
        } else if (isIncoming) {
            state = ActiveCallManager.CallState.CALL_REJECTED;
        } else {
            state = ActiveCallManager.CallState.CALL_CANCELLED;
        }
        ActiveCallManager.getInstance().setCallState(state);
        ActiveCallManager.getInstance().clearActiveCall();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", statusStr);
        updates.put("endedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("calls").document(callId).update(updates);
        TencentChatManager.getInstance().sendTrtcCallSignalMessage(targetUid, callId, isVideo ? "VIDEO" : "VOICE", statusStr, null);

        recordCallMessageInChat(statusStr);

        Toast.makeText(this, "Call " + statusStr.toLowerCase(), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void recordCallMessageInChat(String statusStr) {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null || targetUid == null || targetUid.isEmpty()) return;

        String conversationId = currentUid.compareTo(targetUid) < 0 ? currentUid + "_" + targetUid : targetUid + "_" + currentUid;

        long elapsedSec = callConnectedAtMs > 0 ? (System.currentTimeMillis() - callConnectedAtMs) / 1000 : 0;
        String formattedTime = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        boolean isMissed = "REJECTED".equalsIgnoreCase(statusStr) || "CANCELLED".equalsIgnoreCase(statusStr) || !isCallConnected;

        String callTypeText = isVideo ? "video" : "voice";
        String text;
        if (isMissed) {
            text = "Missed " + callTypeText + " call at " + formattedTime.toLowerCase();
        } else {
            long min = elapsedSec / 60;
            long sec = elapsedSec % 60;
            String durStr = String.format(Locale.US, "%02d:%02d", min, sec);
            text = (isVideo ? "Video call (" : "Voice call (") + durStr + ") at " + formattedTime.toLowerCase();
        }

        String msgId = "call_" + System.currentTimeMillis();

        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put("id", msgId);
        msgMap.put("senderUid", currentUid);
        msgMap.put("targetUid", targetUid);
        msgMap.put("type", "CALL");
        msgMap.put("callType", isVideo ? "VIDEO" : "VOICE");
        msgMap.put("callStatus", statusStr);
        msgMap.put("isMissed", isMissed);
        msgMap.put("text", text);
        msgMap.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("chats")
                .document(conversationId)
                .collection("messages")
                .document(msgId)
                .set(msgMap);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActiveCallManager.getInstance().isActive()) {
            ZegoCallManager.getInstance().setLocalVideoView(tvLocalVideoTexture);
            ZegoCallManager.getInstance().setRemoteVideoView(tvRemoteVideoTexture);
            if (isVideo && isCameraOn) {
                ZegoCallManager.getInstance().enableCamera(true);
            }
            bindCallUI();
            if (ActiveCallManager.getInstance().getCallState() == ActiveCallManager.CallState.CONNECTED && callConnectedAtMs > 0) {
                startCallTimer();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopRingtone();
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
        NotificationHelper.cancelCallNotification(this);
        stopCallTimer();
        if (!ActiveCallManager.getInstance().isActive()) {
            cleanupZegoRoom();
            resetAudioHardwareAfterCall();
        }
        if (callListener != null) {
            callListener.remove();
            callListener = null;
        }
        super.onDestroy();
    }
}
