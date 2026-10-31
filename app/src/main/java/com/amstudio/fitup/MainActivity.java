package com.amstudio.fitup;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
        View fragmentContainer = findViewById(R.id.fragment_container);
        CustomBottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                if (fragmentContainer != null) {
                    fragmentContainer.setPadding(
                            fragmentContainer.getPaddingLeft(),
                            systemBars.top,
                            fragmentContainer.getPaddingRight(),
                            0
                    );
                }

                if (bottomNav != null) {
                    bottomNav.setPadding(
                            bottomNav.getPaddingLeft(),
                            bottomNav.getPaddingTop(),
                            bottomNav.getPaddingRight(),
                            systemBars.bottom
                    );
                }

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

                            ActiveCallManager.getInstance().setActiveCall(callId, callerUid, callerName, initials, isVideo, System.currentTimeMillis());
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SELECTED_TAG, currentTag);
    }

    @Override
    protected void onDestroy() {
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
