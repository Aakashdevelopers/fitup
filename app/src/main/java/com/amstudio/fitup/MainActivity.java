package com.amstudio.fitup;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.amstudio.fitup.repository.ActiveCallManager;
import com.amstudio.fitup.repository.TencentChatManager;
import com.amstudio.fitup.repository.UserRepository;
import com.amstudio.fitup.service.FitUpMessagingService;
import com.amstudio.fitup.view.CustomBottomNavigationView;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_CAMERA = "camera";
    private static final String TAG_CHAT = "chat";
    private static final String TAG_PROFILE = "profile";
    private static final String KEY_SELECTED_TAG = "selected_tag";

    private String currentTag = TAG_CAMERA;
    private ListenerRegistration incomingCallListener;

    private View activeCallBanner;
    private TextView tvBannerTitle;
    private TextView tvBannerSub;
    private TextView tvBannerAvatarInitials;
    private ActiveCallManager.OnCallStateChangeListener activeCallStateListener;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerTimerRunnable;
    private int cachedStatusBarTop = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Register application context for Tencent IM Chat Manager
        TencentChatManager.ContextHolder.setContext(getApplicationContext());

        // Ensure Tencent IM is logged in immediately on app startup for real-time messaging
        TencentChatManager.getInstance().loginWithFirebaseUser(new TencentChatManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {}

            @Override
            public void onError(Exception e) {}
        });

        // Ensure status bar and navigation bar icons are light (white) over the dark navy background
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        // Handle edge-to-edge system insets cleanly
        View rootLayout = findViewById(R.id.main_root);
        CustomBottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                cachedStatusBarTop = systemBars.top;

                if (bottomNav != null) {
                    bottomNav.setPadding(
                            bottomNav.getPaddingLeft(),
                            bottomNav.getPaddingTop(),
                            bottomNav.getPaddingRight(),
                            systemBars.bottom
                    );
                }

                updateLayoutForBannerState();

                return WindowInsetsCompat.CONSUMED;
            });
        }

        if (savedInstanceState != null) {
            currentTag = savedInstanceState.getString(KEY_SELECTED_TAG, TAG_CAMERA);
        }

        setupNavigation(bottomNav, savedInstanceState == null);

        // Save active FCM token to Firestore
        FitUpMessagingService.saveCurrentDeviceToken();

        // Listen for global real-time TRTC incoming calls
        setupGlobalIncomingCallListener();

        // Setup active call banner overlay
        setupActiveCallBanner();
    }

    private void updateLayoutForBannerState() {
        View banner = activeCallBanner != null ? activeCallBanner : findViewById(R.id.layout_active_call_banner);
        View container = findViewById(R.id.fragment_container);
        float density = getResources().getDisplayMetrics().density;

        boolean isBannerVisible = banner != null && banner.getVisibility() == View.VISIBLE;
        boolean isCameraScreen = TAG_CAMERA.equals(currentTag);

        if (isCameraScreen) {
            // Camera Fragment: Toolbar at top, Banner floats underneath toolbar (72dp below status bar)
            if (banner != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) banner.getLayoutParams();
                if (lp != null) {
                    lp.topMargin = cachedStatusBarTop + Math.round(56 * density);
                    banner.setLayoutParams(lp);
                }
            }
            if (container != null) {
                container.setPadding(0, 0, 0, 0);
            }
        } else {
            // Non-Camera Fragments (Chat, Profile, etc.): Banner floats safely below status bar icons
            int topOffset = cachedStatusBarTop > 0 ? cachedStatusBarTop + Math.round(20 * density) : Math.round(52 * density);
            if (banner != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) banner.getLayoutParams();
                if (lp != null) {
                    lp.topMargin = topOffset;
                    banner.setLayoutParams(lp);
                }
            }
            if (container != null) {
                if (isBannerVisible) {
                    int bannerHeight = (banner != null && banner.getHeight() > 0) ? banner.getHeight() : Math.round(64 * density);
                    container.setPadding(0, topOffset + bannerHeight + Math.round(8 * density), 0, 0);
                } else {
                    int topPad = cachedStatusBarTop > 0 ? cachedStatusBarTop : Math.round(28 * density);
                    container.setPadding(0, topPad, 0, 0);
                }
            }
        }
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
                if (activeCallBanner != null) {
                    activeCallBanner.post(this::updateLayoutForBannerState);
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
                    updateLayoutForBannerState();
                    activeCallBanner.post(this::updateLayoutForBannerState);

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
                    updateLayoutForBannerState();
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

    private void setupGlobalIncomingCallListener() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) return;

        incomingCallListener = FirebaseFirestore.getInstance().collection("calls")
                .whereEqualTo("receiverUid", currentUid)
                .whereEqualTo("status", "CALLING")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || isFinishing() || isDestroyed()) return;

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            DocumentSnapshot doc = dc.getDocument();
                            String callId = doc.getId();
                            String callerUid = doc.getString("callerUid");
                            String callerName = doc.getString("callerName");
                            String callType = doc.getString("callType");
                            boolean isVideo = "VIDEO".equalsIgnoreCase(callType);

                            if (callerName == null || callerName.isEmpty() || "You".equals(callerName)) {
                                callerName = "Gym Buddy";
                            }
                            String initials = callerName.length() >= 2 ? callerName.substring(0, 2).toUpperCase() : "GB";

                            TRTCCallActivity.start(
                                    this, callId, callerUid, callerName, initials, isVideo, true
                            );
                        }
                    }
                });
    }

    private void setupNavigation(CustomBottomNavigationView bottomNav, boolean isInitialLaunch) {
        if (bottomNav == null) return;

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_camera) {
                switchFragment(TAG_CAMERA);
                return true;
            } else if (itemId == R.id.nav_chat) {
                switchFragment(TAG_CHAT);
                return true;
            } else if (itemId == R.id.nav_profile) {
                switchFragment(TAG_PROFILE);
                return true;
            }
            return false;
        });

        bottomNav.setOnItemReselectedListener(item -> {
            // Do nothing on re-selection to avoid unnecessary fragment operations
        });

        if (isInitialLaunch) {
            switchFragment(TAG_CAMERA);
            bottomNav.setSelectedItemId(R.id.nav_camera);
        } else {
            syncSelectedNavMenuItem(bottomNav, currentTag);
            switchFragment(currentTag);
        }
    }

    private void switchFragment(String tag) {
        if (isFinishing() || isDestroyed()) return;

        FragmentManager fm = getSupportFragmentManager();
        Fragment targetFragment = fm.findFragmentByTag(tag);

        // Avoid unnecessary transaction if target fragment is already visible
        if (tag.equals(currentTag) && targetFragment != null && targetFragment.isVisible()) {
            return;
        }

        FragmentTransaction transaction = fm.beginTransaction();

        // Hide all currently added fragments in container
        for (Fragment fragment : fm.getFragments()) {
            if (fragment != null && fragment.isAdded()) {
                transaction.hide(fragment);
            }
        }

        if (targetFragment == null) {
            targetFragment = createFragmentForTag(tag);
            transaction.add(R.id.fragment_container, targetFragment, tag);
        } else {
            transaction.show(targetFragment);
        }

        transaction.commit();
        currentTag = tag;
        updateLayoutForBannerState();
    }

    private Fragment createFragmentForTag(String tag) {
        switch (tag) {
            case TAG_CHAT:
                return new ChatFragment();
            case TAG_PROFILE:
                return new ProfileFragment();
            case TAG_CAMERA:
            default:
                return new CameraFragment();
        }
    }

    private void syncSelectedNavMenuItem(CustomBottomNavigationView bottomNav, String tag) {
        int menuId;
        switch (tag) {
            case TAG_CHAT:
                menuId = R.id.nav_chat;
                break;
            case TAG_PROFILE:
                menuId = R.id.nav_profile;
                break;
            case TAG_CAMERA:
            default:
                menuId = R.id.nav_camera;
                break;
        }
        if (bottomNav.getSelectedItemId() != menuId) {
            bottomNav.setSelectedItemId(menuId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UserRepository.getInstance().updateUserOnlineStatus(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        UserRepository.getInstance().updateUserOnlineStatus(false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SELECTED_TAG, currentTag);
    }

    @Override
    protected void onDestroy() {
        UserRepository.getInstance().updateUserOnlineStatus(false);
        stopBannerTimer();
        if (activeCallStateListener != null) {
            ActiveCallManager.getInstance().removeListener(activeCallStateListener);
            activeCallStateListener = null;
        }
        if (incomingCallListener != null) {
            incomingCallListener.remove();
            incomingCallListener = null;
        }
        super.onDestroy();
    }
}
