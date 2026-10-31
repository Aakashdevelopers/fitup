package com.amstudio.fitup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.amstudio.fitup.model.StreakStatus;
import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.StreakRepository;
import com.amstudio.fitup.repository.UserRepository;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {

    private enum TabState {
        POSTS, SNAPS, ACTIVITY
    }

    // Profile Info Views
    private TextView tvDisplayName;
    private TextView tvUsername;
    private TextView tvBio;
    private TextView tvFriendsCount;
    private ImageView ivAvatar;

    private TextView tvActivityStreakVal;
    private TextView tvActivityTotalVal;

    // Tab Views
    private View tabPosts;
    private View tabSnaps;
    private View tabActivity;
    private TextView tvTabPosts;
    private TextView tvTabSnaps;
    private TextView tvTabActivity;
    private View indicatorTabPosts;
    private View indicatorTabSnaps;
    private View indicatorTabActivity;

    // Empty State Views
    private ImageView ivEmptyIcon;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;

    private ActivityResultLauncher<Intent> editProfileLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        populateUserData();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        populateUserData();
        setupClickListeners(view);
        updateTabState(TabState.POSTS);
    }

    private void initViews(@NonNull View view) {
        tvDisplayName = view.findViewById(R.id.tv_display_name);
        tvUsername = view.findViewById(R.id.tv_username);
        tvBio = view.findViewById(R.id.tv_bio);
        tvFriendsCount = view.findViewById(R.id.tv_stat_followers_value);
        ivAvatar = view.findViewById(R.id.iv_avatar);

        tvActivityStreakVal = view.findViewById(R.id.tv_activity_streak_val);
        tvActivityTotalVal = view.findViewById(R.id.tv_activity_total_val);

        // Tabs
        tabPosts = view.findViewById(R.id.tab_posts);
        tabSnaps = view.findViewById(R.id.tab_snaps);
        tabActivity = view.findViewById(R.id.tab_activity);

        tvTabPosts = view.findViewById(R.id.tv_tab_posts);
        tvTabSnaps = view.findViewById(R.id.tv_tab_snaps);
        tvTabActivity = view.findViewById(R.id.tv_tab_activity);

        indicatorTabPosts = view.findViewById(R.id.indicator_tab_posts);
        indicatorTabSnaps = view.findViewById(R.id.indicator_tab_snaps);
        indicatorTabActivity = view.findViewById(R.id.indicator_tab_activity);

        // Empty state
        ivEmptyIcon = view.findViewById(R.id.iv_empty_icon);
        tvEmptyTitle = view.findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = view.findViewById(R.id.tv_empty_subtitle);
    }

    private void populateUserData() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid != null) {
            UserRepository.getInstance().getUserProfile(currentUid, new UserRepository.ProfileCallback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    if (isAdded() && profile != null) {
                        if (tvDisplayName != null && !TextUtils.isEmpty(profile.getDisplayName())) {
                            tvDisplayName.setText(profile.getDisplayName());
                        }
                        if (tvUsername != null && !TextUtils.isEmpty(profile.getUsername())) {
                            tvUsername.setText("@" + profile.getUsername());
                        }
                        if (tvBio != null) {
                            if (!TextUtils.isEmpty(profile.getBio())) {
                                tvBio.setText(profile.getBio());
                            } else {
                                tvBio.setText(R.string.profile_default_bio);
                            }
                        }
                        if (tvFriendsCount != null) {
                            tvFriendsCount.setText(String.valueOf(profile.getFriendsCount()));
                        }
                        if (ivAvatar != null) {
                            if (!TextUtils.isEmpty(profile.getAvatarUrl())) {
                                Glide.with(ProfileFragment.this)
                                        .load(profile.getAvatarUrl())
                                        .placeholder(R.drawable.ic_avatar_placeholder)
                                        .into(ivAvatar);
                            } else {
                                ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
                            }
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null && isAdded()) {
                        if (tvDisplayName != null && !TextUtils.isEmpty(currentUser.getDisplayName())) {
                            tvDisplayName.setText(currentUser.getDisplayName());
                        }
                        if (tvUsername != null && !TextUtils.isEmpty(currentUser.getEmail())) {
                            tvUsername.setText(currentUser.getEmail());
                        }
                    }
                }
            });

            StreakRepository.getInstance().getStreakStatus(new StreakRepository.StreakStatusCallback() {
                @Override
                public void onSuccess(StreakStatus status) {
                    if (isAdded() && status != null) {
                        if (tvActivityStreakVal != null) {
                            tvActivityStreakVal.setText(status.getCurrentStreak() + " Days");
                        }
                        if (tvActivityTotalVal != null) {
                            tvActivityTotalVal.setText(String.valueOf(status.getTotalCheckIns()));
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Silent fallback
                }
            });
        }
    }

    private void setupClickListeners(@NonNull View view) {
        View btnSettingsTop = view.findViewById(R.id.btn_settings_top);
        if (btnSettingsTop != null) {
            btnSettingsTop.setOnClickListener(v -> showToast("Settings & Preferences"));
        }

        View cardStreak = view.findViewById(R.id.card_current_streak);
        if (cardStreak != null) {
            cardStreak.setOnClickListener(v -> {
                if (getContext() != null) {
                    Intent intent = new Intent(getContext(), StreakActivity.class);
                    startActivity(intent);
                }
            });
        }

        View btnEditAvatar = view.findViewById(R.id.btn_edit_avatar);
        if (btnEditAvatar != null) {
            btnEditAvatar.setOnClickListener(v -> openEditProfileActivity());
        }

        MaterialButton btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> openEditProfileActivity());
        }

        MaterialButton btnShareProfile = view.findViewById(R.id.btn_share_profile);
        if (btnShareProfile != null) {
            btnShareProfile.setOnClickListener(v -> showToast("Profile link copied to clipboard!"));
        }

        View statPosts = view.findViewById(R.id.stat_posts_container);
        if (statPosts != null) {
            statPosts.setOnClickListener(v -> updateTabState(TabState.POSTS));
        }

        View statFollowers = view.findViewById(R.id.stat_followers_container);
        if (statFollowers != null) {
            statFollowers.setOnClickListener(v -> showToast("Followers List"));
        }

        View statFollowing = view.findViewById(R.id.stat_following_container);
        if (statFollowing != null) {
            statFollowing.setOnClickListener(v -> showToast("Following List"));
        }

        if (tabPosts != null) {
            tabPosts.setOnClickListener(v -> updateTabState(TabState.POSTS));
        }
        if (tabSnaps != null) {
            tabSnaps.setOnClickListener(v -> updateTabState(TabState.SNAPS));
        }
        if (tabActivity != null) {
            tabActivity.setOnClickListener(v -> updateTabState(TabState.ACTIVITY));
        }

        View rowEditPersonal = view.findViewById(R.id.row_edit_personal_details);
        if (rowEditPersonal != null) {
            rowEditPersonal.setOnClickListener(v -> openEditProfileActivity());
        }

        View rowPrivacy = view.findViewById(R.id.row_privacy_security);
        if (rowPrivacy != null) {
            rowPrivacy.setOnClickListener(v -> showToast("Privacy & Security Settings"));
        }

        View rowNotifications = view.findViewById(R.id.row_notifications);
        if (rowNotifications != null) {
            rowNotifications.setOnClickListener(v -> showToast("Notification Preferences"));
        }

        View rowHelp = view.findViewById(R.id.row_help_support);
        if (rowHelp != null) {
            rowHelp.setOnClickListener(v -> showToast("Help & Support Center"));
        }

        View rowLogout = view.findViewById(R.id.row_logout);
        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
        }
    }

    private void openEditProfileActivity() {
        if (getContext() != null) {
            Intent intent = new Intent(getContext(), EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        }
    }

    private void updateTabState(TabState state) {
        Context context = getContext();
        if (context == null) return;

        int activeColor = ContextCompat.getColor(context, R.color.electric_blue);
        int inactiveColor = ContextCompat.getColor(context, R.color.secondary_text);

        if (tvTabPosts != null) tvTabPosts.setTextColor(inactiveColor);
        if (tvTabSnaps != null) tvTabSnaps.setTextColor(inactiveColor);
        if (tvTabActivity != null) tvTabActivity.setTextColor(inactiveColor);

        if (indicatorTabPosts != null) indicatorTabPosts.setVisibility(View.INVISIBLE);
        if (indicatorTabSnaps != null) indicatorTabSnaps.setVisibility(View.INVISIBLE);
        if (indicatorTabActivity != null) indicatorTabActivity.setVisibility(View.INVISIBLE);

        switch (state) {
            case POSTS:
                if (tvTabPosts != null) tvTabPosts.setTextColor(activeColor);
                if (indicatorTabPosts != null) indicatorTabPosts.setVisibility(View.VISIBLE);
                if (ivEmptyIcon != null) ivEmptyIcon.setImageResource(R.drawable.ic_posts_grid);
                if (tvEmptyTitle != null) tvEmptyTitle.setText(R.string.profile_empty_posts_title);
                if (tvEmptySubtitle != null) tvEmptySubtitle.setText(R.string.profile_empty_posts_sub);
                break;

            case SNAPS:
                if (tvTabSnaps != null) tvTabSnaps.setTextColor(activeColor);
                if (indicatorTabSnaps != null) indicatorTabSnaps.setVisibility(View.VISIBLE);
                if (ivEmptyIcon != null) ivEmptyIcon.setImageResource(R.drawable.ic_snap_video);
                if (tvEmptyTitle != null) tvEmptyTitle.setText(R.string.profile_empty_snaps_title);
                if (tvEmptySubtitle != null) tvEmptySubtitle.setText(R.string.profile_empty_snaps_sub);
                break;

            case ACTIVITY:
                if (tvTabActivity != null) tvTabActivity.setTextColor(activeColor);
                if (indicatorTabActivity != null) indicatorTabActivity.setVisibility(View.VISIBLE);
                if (ivEmptyIcon != null) ivEmptyIcon.setImageResource(R.drawable.ic_activity_pulse);
                if (tvEmptyTitle != null) tvEmptyTitle.setText(R.string.profile_empty_activity_title);
                if (tvEmptySubtitle != null) tvEmptySubtitle.setText(R.string.profile_empty_activity_sub);
                break;
        }
    }

    private void showLogoutConfirmationDialog() {
        Context context = getContext();
        if (context == null) return;

        new AlertDialog.Builder(context)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of FitUp?")
                .setPositiveButton("Log Out", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(context, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        populateUserData();
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
