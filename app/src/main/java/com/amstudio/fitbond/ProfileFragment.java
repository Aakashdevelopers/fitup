package com.amstudio.fitbond;

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
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.graphics.Color;
import android.util.TypedValue;
import com.amstudio.fitbond.repository.SnapRepository;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.Map;

import android.net.Uri;

import com.amstudio.fitbond.helper.OneSignalManager;
import com.amstudio.fitbond.model.StreakStatus;
import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.FriendRepository;
import com.amstudio.fitbond.repository.StreakRepository;
import com.amstudio.fitbond.repository.TencentChatManager;
import com.amstudio.fitbond.repository.UserRepository;
import com.amstudio.fitbond.util.AvatarLoader;
import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private enum TabState {
        POSTS, SNAPS, ACTIVITY
    }

    // Profile Info Views
    private TextView tvProfileTitle;
    private TextView tvDisplayName;
    private TextView tvUsername;
    private TextView tvBio;
    private TextView tvFriendsCount;
    private ImageView ivAvatar;
    private TextView tvProfileLocation;
    private View layoutProfileLocation;

    private TextView tvStatPostsValue;
    private TextView tvStatFollowersValue;
    private TextView tvStatFollowingValue;

    private ListenerRegistration userProfileRegistration;
    private ListenerRegistration postsRegistration;
    private ListenerRegistration sentPostsRegistration;
    private ListenerRegistration receivedSnapsRegistration;
    private ListenerRegistration followersRegistration;
    private ListenerRegistration followingRegistration;
    private final Map<String, ProfilePostItem> snapPostMap = new java.util.concurrent.ConcurrentHashMap<>();

    private TextView tvActivityDaysVal;
    private TextView tvActivityStreakVal;
    private TextView tvActivityTotalVal;
    private View containerActivityCards;

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
    private View containerEmptyState;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshProfile;

    // Posts Grid Views
    private RecyclerView rvPostsGrid;
    private final List<ProfilePostItem> allUserPosts = new ArrayList<>();
    private final List<ProfilePostItem> savedActivityPosts = new ArrayList<>();
    private final List<ProfilePostItem> currentTabPosts = new ArrayList<>();
    private ProfilePostAdapter gridAdapter;
    private ProfileSnapCardAdapter profileSnapCardAdapter;
    private final Map<String, ProfileSnapMessageHolder> authorizedSnapMap = new java.util.concurrent.ConcurrentHashMap<>();
    private TabState currentTabState = TabState.POSTS;
    private ListenerRegistration bookmarksRegistration;

    private ActivityResultLauncher<Intent> editProfileLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> createPostMediaLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        UserRepository.getInstance().invalidateCache();
                        populateUserData();
                    }
                }
        );

        createPostMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null && getContext() != null) {
                        Intent intent = new Intent(getContext(), SnapPreviewActivity.class);
                        intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, uri);
                        intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_PHOTO);
                        startActivity(intent);
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

        View topBar = view.findViewById(R.id.top_app_bar);
        if (topBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
                int statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                float density = getResources().getDisplayMetrics().density;
                int extraTopPadding = Math.round(14 * density);
                int extraBottomPadding = Math.round(8 * density);
                topBar.setPadding(
                        topBar.getPaddingLeft(),
                        statusBarTop + extraTopPadding,
                        topBar.getPaddingRight(),
                        extraBottomPadding
                );
                return insets;
            });
            view.post(() -> ViewCompat.requestApplyInsets(view));
        }
    }

    private void initViews(@NonNull View view) {
        tvProfileTitle = view.findViewById(R.id.tv_profile_title);
        tvDisplayName = view.findViewById(R.id.tv_display_name);
        tvUsername = view.findViewById(R.id.tv_username);
        tvBio = view.findViewById(R.id.tv_bio);
        tvStatPostsValue = view.findViewById(R.id.tv_stat_posts_value);
        tvStatFollowersValue = view.findViewById(R.id.tv_stat_followers_value);
        tvStatFollowingValue = view.findViewById(R.id.tv_stat_following_value);
        tvFriendsCount = tvStatFollowersValue;
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvProfileLocation = view.findViewById(R.id.tv_profile_location);
        layoutProfileLocation = view.findViewById(R.id.layout_profile_location);

        tvActivityDaysVal = view.findViewById(R.id.tv_activity_days_val);
        tvActivityStreakVal = view.findViewById(R.id.tv_activity_streak_val);
        tvActivityTotalVal = view.findViewById(R.id.tv_activity_total_val);
        containerActivityCards = view.findViewById(R.id.container_activity_cards);

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
        containerEmptyState = view.findViewById(R.id.container_empty_state);

        rvPostsGrid = view.findViewById(R.id.rv_profile_posts_grid);
        setupPostsGridRecyclerView();

        swipeRefreshProfile = view.findViewById(R.id.swipe_refresh_profile);
        if (swipeRefreshProfile != null) {
            swipeRefreshProfile.setColorSchemeResources(R.color.electric_blue, R.color.primary_blue);
            swipeRefreshProfile.setOnRefreshListener(() -> {
                UserRepository.getInstance().invalidateCache();
                populateUserData();
                setupRealtimeStatsListeners();
                swipeRefreshProfile.postDelayed(() -> {
                    if (swipeRefreshProfile != null) {
                        swipeRefreshProfile.setRefreshing(false);
                    }
                }, 800);
            });
        }
    }

    private void populateUserData() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid != null) {
            UserRepository.getInstance().getUserProfile(currentUid, new UserRepository.ProfileCallback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    if (isAdded() && profile != null) {
                        if (tvProfileTitle != null) {
                            if (!TextUtils.isEmpty(profile.getUsername())) {
                                tvProfileTitle.setText(profile.getUsername());
                            } else if (!TextUtils.isEmpty(profile.getDisplayName())) {
                                tvProfileTitle.setText(profile.getDisplayName());
                            }
                        }
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
                            AvatarLoader.loadAvatar(ProfileFragment.this, profile.getAvatarUrl(), ivAvatar);
                        }
                        updateLocationPill(profile);
                    }
                }

                @Override
                public void onError(Exception e) {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null && isAdded()) {
                        if (tvProfileTitle != null && !TextUtils.isEmpty(currentUser.getDisplayName())) {
                            tvProfileTitle.setText(currentUser.getDisplayName());
                        }
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
                        if (tvActivityDaysVal != null) {
                            tvActivityDaysVal.setText(status.getTotalCheckIns() + " Days");
                        }
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

    private void updateLocationPill(UserProfile profile) {
        if (profile == null) return;
        boolean showLocation = profile.isShowLocation();
        String locStr = profile.getLocation();
        if (showLocation) {
            String displayLocation = !TextUtils.isEmpty(locStr) ? locStr : "Fitness Enthusiast";
            if (tvProfileLocation != null) tvProfileLocation.setText(displayLocation);
            if (layoutProfileLocation != null) layoutProfileLocation.setVisibility(View.VISIBLE);
        } else {
            if (layoutProfileLocation != null) layoutProfileLocation.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners(@NonNull View view) {
        View btnAddPostTop = view.findViewById(R.id.btn_add_post_top);
        if (btnAddPostTop != null) {
            btnAddPostTop.setOnClickListener(v -> showCreateContentBottomSheet());
        }

        View btnSettingsTop = view.findViewById(R.id.btn_settings_top);
        if (btnSettingsTop != null) {
            btnSettingsTop.setOnClickListener(v -> {
                SettingsBottomSheetDialog dialog = SettingsBottomSheetDialog.newInstance();
                dialog.show(getParentFragmentManager(), "SettingsBottomSheetDialog");
            });
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

        View statPosts = view.findViewById(R.id.stat_posts_container);
        if (statPosts != null) {
            statPosts.setOnClickListener(v -> updateTabState(TabState.POSTS));
        }

        View statFollowers = view.findViewById(R.id.stat_followers_container);
        if (statFollowers != null) {
            statFollowers.setOnClickListener(v -> {
                String currentUid = UserRepository.getInstance().getCurrentUid();
                FollowersListDialog dialog = FollowersListDialog.newInstance(currentUid, "Followers");
                dialog.show(getParentFragmentManager(), "FollowersListDialog");
            });
        }

        View statFollowing = view.findViewById(R.id.stat_following_container);
        if (statFollowing != null) {
            statFollowing.setOnClickListener(v -> {
                String currentUid = UserRepository.getInstance().getCurrentUid();
                FollowersListDialog dialog = FollowersListDialog.newInstance(currentUid, "Following");
                dialog.show(getParentFragmentManager(), "FollowersListDialog");
            });
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

        View btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> openEditProfileActivity());
        }

        View btnShareProfile = view.findViewById(R.id.btn_share_profile);
        if (btnShareProfile != null) {
            btnShareProfile.setOnClickListener(v -> showToast("Profile link copied to clipboard!"));
        }

    }

    private void openEditProfileActivity() {
        if (getContext() != null) {
            Intent intent = new Intent(getContext(), EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        }
    }

    private void showCreateContentBottomSheet() {
        CreateContentBottomSheetDialog dialog = CreateContentBottomSheetDialog.newInstance();
        dialog.setOnCreateOptionSelectedListener(new CreateContentBottomSheetDialog.OnCreateOptionSelectedListener() {
            @Override
            public void onSelectPost() {
                if (getContext() != null) {
                    Intent intent = new Intent(getContext(), NewPostActivity.class);
                    startActivity(intent);
                }
            }

            @Override
            public void onSelectStory() {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToTab(MainActivity.TAG_CAMERA);
                }
            }
        });
        dialog.show(getParentFragmentManager(), "CreateContentBottomSheetDialog");
    }

    private void setupPostsGridRecyclerView() {
        if (rvPostsGrid == null || getContext() == null) return;

        gridAdapter = new ProfilePostAdapter(currentTabPosts, item -> {
            String currentUid = UserRepository.getInstance().getCurrentUid();
            ArrayList<String> urls = item.mediaUrls != null ? new ArrayList<>(item.mediaUrls) : new ArrayList<>();
            PostDetailActivity.open(
                    getContext(),
                    item.id,
                    item.mediaUrl,
                    urls,
                    item.title,
                    item.caption,
                    currentUid
            );
        });

        profileSnapCardAdapter = new ProfileSnapCardAdapter(
                message -> {
                    if (message == null || !isAdded()) return;
                    boolean isVideo = message.getSnapKind() == ChatMessage.SnapKind.VIDEO;
                    SnapViewerDialog dialog = SnapViewerDialog.newInstance(
                            message.getSenderName() != null ? message.getSenderName() : "You",
                            message.getSenderName() != null && message.getSenderName().length() >= 2 ? message.getSenderName().substring(0, 2).toUpperCase() : "GB",
                            R.drawable.bg_avatar_gradient_1,
                            isVideo,
                            message.getDurationText(),
                            message.getId(),
                            message.getMediaUri()
                    );
                    dialog.show(getParentFragmentManager(), "SnapViewerDialog");
                },
                (message, anchorView) -> showSnapOptionsDialogForProfile(message)
        );

        rvPostsGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
        rvPostsGrid.setAdapter(gridAdapter);
    }

    private void updateTabState(TabState state) {
        currentTabState = state;
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

        renderPostsGridForCurrentTab();
    }

    private void showLogoutConfirmationDialog() {
        Context context = getContext();
        if (context == null) return;

        new AlertDialog.Builder(context)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of FitBond?")
                .setPositiveButton("Log Out", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        UserRepository.getInstance().updateUserOnlineStatus(false);
        TencentChatManager.getInstance().logout(null);
        OneSignalManager.getInstance().logout();
        FirebaseAuth.getInstance().signOut();
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(context, AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        setupRealtimeStatsListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        UserRepository.getInstance().invalidateCache();
        populateUserData();
    }

    @Override
    public void onStop() {
        super.onStop();
        removeStatsListeners();
    }

    private void setupRealtimeStatsListeners() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) return;

        removeStatsListeners();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. User Profile Document Real-Time Listener
        userProfileRegistration = db.collection("users").document(currentUid)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists() || !isAdded()) return;
                    UserProfile profile = doc.toObject(UserProfile.class);
                    if (profile != null) {
                        if (tvProfileTitle != null) {
                            if (!TextUtils.isEmpty(profile.getUsername())) {
                                tvProfileTitle.setText(profile.getUsername());
                            } else if (!TextUtils.isEmpty(profile.getDisplayName())) {
                                tvProfileTitle.setText(profile.getDisplayName());
                            }
                        }
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
                        if (ivAvatar != null) {
                            AvatarLoader.loadAvatar(ProfileFragment.this, profile.getAvatarUrl(), ivAvatar);
                        }
                        updateLocationPill(profile);
                    }
                });

        // 2. Real-Time Sent & Received Snaps/Posts Listeners
        if (sentPostsRegistration != null) sentPostsRegistration.remove();
        if (receivedSnapsRegistration != null) receivedSnapsRegistration.remove();

        sentPostsRegistration = db.collection("snaps")
                .whereEqualTo("senderUid", currentUid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !isAdded()) return;
                    processSnapDocs(snapshot.getDocuments());
                });

        receivedSnapsRegistration = db.collection("snaps")
                .whereArrayContains("recipientUids", currentUid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !isAdded()) return;
                    processSnapDocs(snapshot.getDocuments());
                });

        // 3. Real-Time Followers & Following Count Listener
        followersRegistration = db.collection("friendships")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || !isAdded()) return;
                    FriendRepository.getInstance().invalidateFriendsCache();
                    FriendRepository.getInstance().getFollowers(currentUid, new FriendRepository.FriendsListCallback() {
                        @Override
                        public void onSuccess(List<UserProfile> friends) {
                            if (isAdded() && tvStatFollowersValue != null) {
                                tvStatFollowersValue.setText(String.valueOf(friends != null ? friends.size() : 0));
                            }
                        }

                        @Override
                        public void onError(Exception ex) {
                            if (isAdded() && tvStatFollowersValue != null) {
                                tvStatFollowersValue.setText("0");
                            }
                        }
                    });

                    FriendRepository.getInstance().getFollowing(currentUid, new FriendRepository.FriendsListCallback() {
                        @Override
                        public void onSuccess(List<UserProfile> friends) {
                            if (isAdded() && tvStatFollowingValue != null) {
                                tvStatFollowingValue.setText(String.valueOf(friends != null ? friends.size() : 0));
                            }
                        }

                        @Override
                        public void onError(Exception ex) {
                            if (isAdded() && tvStatFollowingValue != null) {
                                tvStatFollowingValue.setText("0");
                            }
                        }
                    });
                });

        loadActivityBookmarks(currentUid);
    }

    private void loadActivityBookmarks(String currentUid) {
        if (bookmarksRegistration != null) bookmarksRegistration.remove();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        bookmarksRegistration = db.collection("users").document(currentUid)
                .collection("bookmarks")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !isAdded()) return;
                    savedActivityPosts.clear();
                    List<String> postIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String pId = doc.getString("postId");
                        if (pId == null) pId = doc.getId();
                        if (pId != null && !pId.isEmpty()) postIds.add(pId);
                    }

                    if (postIds.isEmpty()) {
                        renderPostsGridForCurrentTab();
                        return;
                    }

                    int batchSize = 30;
                    for (int i = 0; i < postIds.size(); i += batchSize) {
                        List<String> batch = postIds.subList(i, Math.min(i + batchSize, postIds.size()));
                        db.collection("snaps").whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch).get()
                                .addOnSuccessListener(q -> {
                                    if (!isAdded() || q == null) return;
                                    for (DocumentSnapshot doc : q.getDocuments()) {
                                        String id = doc.getId();
                                        String title = doc.getString("title");
                                        String caption = doc.getString("caption");
                                        String mediaUrl = doc.getString("mediaUrl");
                                        String mediaType = doc.getString("mediaType");
                                        Timestamp ts = doc.getTimestamp("createdAt");
                                        long createdAtMs = ts != null ? ts.toDate().getTime() : System.currentTimeMillis();

                                        if (mediaUrl != null && !mediaUrl.isEmpty()) {
                                            savedActivityPosts.add(new ProfilePostItem(id, title, caption, mediaUrl, mediaType, createdAtMs));
                                        }
                                    }
                                    savedActivityPosts.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));
                                    renderPostsGridForCurrentTab();
                                });
                    }
                });
    }

    private void processSnapDocs(List<DocumentSnapshot> docs) {
        if (docs == null) return;
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) return;

        for (DocumentSnapshot doc : docs) {
            String id = doc.getId();
            String title = doc.getString("title");
            String caption = doc.getString("caption");
            String mediaUrl = doc.getString("mediaUrl");
            if (TextUtils.isEmpty(mediaUrl)) mediaUrl = doc.getString("url");
            if (TextUtils.isEmpty(mediaUrl)) mediaUrl = doc.getString("downloadUrl");
            if (TextUtils.isEmpty(mediaUrl)) mediaUrl = doc.getString("imageUrl");
            if (TextUtils.isEmpty(mediaUrl)) mediaUrl = doc.getString("photoUrl");
            if (TextUtils.isEmpty(mediaUrl)) mediaUrl = doc.getString("image");
            if (TextUtils.isEmpty(mediaUrl)) mediaUrl = doc.getString("media");
            if (TextUtils.isEmpty(mediaUrl)) mediaUrl = doc.getString("storagePath");

            String mediaType = doc.getString("mediaType");
            if (mediaType == null) mediaType = doc.getString("type");
            Timestamp ts = doc.getTimestamp("createdAt");
            long createdAtMs = ts != null ? ts.toDate().getTime() : System.currentTimeMillis();

            List<String> mediaUrls = (List<String>) doc.get("mediaUrls");
            List<String> recipientUids = (List<String>) doc.get("recipientUids");
            String senderUid = doc.getString("senderUid");

            boolean isSentByMe = currentUid.equals(senderUid);
            boolean isRecipientMe = recipientUids != null && recipientUids.contains(currentUid);
            boolean isSnapDoc = isSentByMe || isRecipientMe || (recipientUids != null && !recipientUids.isEmpty()) || "SNAP".equalsIgnoreCase(mediaType);

            if (isSentByMe || isRecipientMe) {
                boolean isVideo = "VIDEO".equalsIgnoreCase(mediaType);
                ChatMessage.MessageType msgType = isSentByMe ? ChatMessage.MessageType.SNAP_OUTGOING : ChatMessage.MessageType.SNAP_INCOMING;
                ChatMessage.SnapKind snapKind = isVideo ? ChatMessage.SnapKind.VIDEO : ChatMessage.SnapKind.PHOTO;

                Long durObj = doc.getLong("durationSeconds");
                long durSec = durObj != null ? durObj : 10;
                String durationStr = isVideo ? "0:" + durSec : durSec + "s";
                String label = isVideo ? "Video Snap" : "Snap";

                java.text.SimpleDateFormat timeSdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US);
                String formattedTime = timeSdf.format(new java.util.Date(createdAtMs));

                ChatMessage msg = new ChatMessage(
                        id,
                        isSentByMe ? "me" : (senderUid != null ? senderUid : "friend"),
                        isSentByMe ? "You" : "Friend",
                        label,
                        msgType,
                        snapKind,
                        formattedTime,
                        durationStr,
                        mediaUrl,
                        false
                );

                Long replaysObj = doc.getLong("replayCount");
                if (replaysObj != null) msg.setReplayCount(replaysObj.intValue());
                Boolean isSavedObj = doc.getBoolean("isSaved");
                if (isSavedObj != null) msg.setSavedInChat(isSavedObj);
                Boolean isExpiredObj = doc.getBoolean("isExpired");
                if (isExpiredObj != null) msg.setExpired(isExpiredObj);

                authorizedSnapMap.put(id, new ProfileSnapMessageHolder(msg, createdAtMs));
            }

            if (!TextUtils.isEmpty(mediaUrl) || (mediaUrls != null && !mediaUrls.isEmpty())) {
                snapPostMap.put(id, new ProfilePostItem(id, title, caption, mediaUrl, mediaUrls, mediaType != null ? mediaType : "SNAP", createdAtMs, isSnapDoc));
            }
        }

        allUserPosts.clear();
        allUserPosts.addAll(snapPostMap.values());
        allUserPosts.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));

        if (tvStatPostsValue != null) {
            long publicPostsCount = 0;
            for (ProfilePostItem item : allUserPosts) {
                if (!item.isSnap) publicPostsCount++;
            }
            tvStatPostsValue.setText(String.valueOf(publicPostsCount));
        }

        renderPostsGridForCurrentTab();
    }

    private String getDateHeaderString(long epochMs) {
        if (epochMs <= 0) return "Today";

        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar itemCal = java.util.Calendar.getInstance();
        itemCal.setTimeInMillis(epochMs);

        if (now.get(java.util.Calendar.YEAR) == itemCal.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == itemCal.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "Today";
        }

        now.add(java.util.Calendar.DAY_OF_YEAR, -1);
        if (now.get(java.util.Calendar.YEAR) == itemCal.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == itemCal.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, dd MMM", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(epochMs));
    }

    private void removeStatsListeners() {
        if (userProfileRegistration != null) {
            userProfileRegistration.remove();
            userProfileRegistration = null;
        }
        if (postsRegistration != null) {
            postsRegistration.remove();
            postsRegistration = null;
        }
        if (sentPostsRegistration != null) {
            sentPostsRegistration.remove();
            sentPostsRegistration = null;
        }
        if (receivedSnapsRegistration != null) {
            receivedSnapsRegistration.remove();
            receivedSnapsRegistration = null;
        }
        if (followersRegistration != null) {
            followersRegistration.remove();
            followersRegistration = null;
        }
        if (followingRegistration != null) {
            followingRegistration.remove();
            followingRegistration = null;
        }
        if (bookmarksRegistration != null) {
            bookmarksRegistration.remove();
            bookmarksRegistration = null;
        }
    }

    private void renderPostsGridForCurrentTab() {
        if (getContext() == null || rvPostsGrid == null) return;

        if (containerActivityCards != null) {
            containerActivityCards.setVisibility(currentTabState == TabState.ACTIVITY ? View.VISIBLE : View.GONE);
        }

        if (currentTabState == TabState.SNAPS) {
            rvPostsGrid.setLayoutManager(new LinearLayoutManager(getContext()));
            rvPostsGrid.setAdapter(profileSnapCardAdapter);

            List<ProfileSnapMessageHolder> snapHolders = new ArrayList<>(authorizedSnapMap.values());
            snapHolders.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));

            List<ProfileSnapCardItem> cardItems = new ArrayList<>();
            String lastHeader = null;

            for (ProfileSnapMessageHolder h : snapHolders) {
                String dateHeader = getDateHeaderString(h.createdAtMs);
                if (lastHeader == null || !lastHeader.equalsIgnoreCase(dateHeader)) {
                    lastHeader = dateHeader;
                    cardItems.add(new ProfileSnapCardItem(dateHeader));
                }
                int type = h.message.isOutgoing() ? ProfileSnapCardItem.TYPE_SNAP_OUTGOING : ProfileSnapCardItem.TYPE_SNAP_INCOMING;
                cardItems.add(new ProfileSnapCardItem(h.message, type));
            }

            if (profileSnapCardAdapter != null) {
                profileSnapCardAdapter.setItems(cardItems);
            }

            boolean hasSnaps = !cardItems.isEmpty();
            if (rvPostsGrid != null) rvPostsGrid.setVisibility(hasSnaps ? View.VISIBLE : View.GONE);
            if (containerEmptyState != null) containerEmptyState.setVisibility(hasSnaps ? View.GONE : View.VISIBLE);

        } else {
            rvPostsGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
            rvPostsGrid.setAdapter(gridAdapter);

            currentTabPosts.clear();

            for (ProfilePostItem item : allUserPosts) {
                switch (currentTabState) {
                    case ACTIVITY:
                        if ("ACTIVITY".equalsIgnoreCase(item.mediaType) || "WORKOUT".equalsIgnoreCase(item.mediaType)) {
                            currentTabPosts.add(item);
                        }
                        break;
                    case POSTS:
                    default:
                        if (!item.isSnap && !"ACTIVITY".equalsIgnoreCase(item.mediaType) && !"WORKOUT".equalsIgnoreCase(item.mediaType)) {
                            currentTabPosts.add(item);
                        }
                        break;
                }
            }

            if (currentTabState == TabState.ACTIVITY) {
                for (ProfilePostItem savedItem : savedActivityPosts) {
                    boolean duplicate = false;
                    for (ProfilePostItem existing : currentTabPosts) {
                        if (existing.id != null && existing.id.equals(savedItem.id)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        currentTabPosts.add(savedItem);
                    }
                }
                currentTabPosts.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));
            }

            if (gridAdapter != null) {
                gridAdapter.notifyDataSetChanged();
            }

            boolean hasPosts = !currentTabPosts.isEmpty();
            if (rvPostsGrid != null) {
                rvPostsGrid.setVisibility(hasPosts ? View.VISIBLE : View.GONE);
            }
            if (containerEmptyState != null) {
                containerEmptyState.setVisibility(hasPosts ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void showSnapOptionsDialogForProfile(ChatMessage message) {
        if (message == null || getContext() == null) return;
        SnapOptionsDialog dialog = SnapOptionsDialog.newInstance(
                message.getReplayCount(),
                message.isSavedInChat(),
                message.isExpired()
        );
        dialog.setOnSnapOptionSelectedListener(new SnapOptionsDialog.OnSnapOptionSelectedListener() {
            @Override
            public void onReplaySelected() {
                if (message.isExpired() || message.getRemainingReplays() <= 0) {
                    showToast("Maximum 3 replays reached for this snap");
                    return;
                }
                String currentUid = UserRepository.getInstance().getCurrentUid();
                String conversationId = currentUid != null && message.getSenderId() != null
                        ? (currentUid.compareTo(message.getSenderId()) < 0 ? currentUid + "_" + message.getSenderId() : message.getSenderId() + "_" + currentUid)
                        : "conversation";

                SnapRepository.getInstance().incrementSnapReplayCount(conversationId, message.getId(), message.getId(), new SnapRepository.SnapReplayCallback() {
                    @Override
                    public void onSuccess(int newReplayCount, boolean isExpired) {
                        message.setReplayCount(newReplayCount);
                        if (isExpired) {
                            message.setExpired(true);
                        }
                        if (profileSnapCardAdapter != null) profileSnapCardAdapter.notifyDataSetChanged();
                        if (!isExpired) {
                            boolean isVideo = message.getSnapKind() == ChatMessage.SnapKind.VIDEO;
                            SnapViewerDialog snapViewer = SnapViewerDialog.newInstance(
                                    message.getSenderName(),
                                    "GB",
                                    R.drawable.bg_avatar_gradient_1,
                                    isVideo,
                                    message.getDurationText(),
                                    message.getId(),
                                    message.getMediaUri()
                            );
                            snapViewer.show(getParentFragmentManager(), "SnapViewerDialog");
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        showToast("Failed to replay snap");
                    }
                });
            }

            @Override
            public void onSaveToggleSelected(boolean currentSavedState) {
                boolean newSavedState = !currentSavedState;
                String currentUid = UserRepository.getInstance().getCurrentUid();
                String conversationId = currentUid != null && message.getSenderId() != null
                        ? (currentUid.compareTo(message.getSenderId()) < 0 ? currentUid + "_" + message.getSenderId() : message.getSenderId() + "_" + currentUid)
                        : "conversation";

                SnapRepository.getInstance().setSnapSavedStatus(conversationId, message.getId(), newSavedState, new SnapRepository.SnapActionCallback() {
                    @Override
                    public void onSuccess() {
                        message.setSavedInChat(newSavedState);
                        if (profileSnapCardAdapter != null) profileSnapCardAdapter.notifyDataSetChanged();
                        showToast(newSavedState ? "Snap saved in chat 💾" : "Snap unsaved");
                    }

                    @Override
                    public void onError(Exception e) {
                        showToast("Failed to update save status");
                    }
                });
            }

            @Override
            public void onDeleteSelected() {
                String currentUid = UserRepository.getInstance().getCurrentUid();
                String conversationId = currentUid != null && message.getSenderId() != null
                        ? (currentUid.compareTo(message.getSenderId()) < 0 ? currentUid + "_" + message.getSenderId() : message.getSenderId() + "_" + currentUid)
                        : "conversation";

                SnapRepository.getInstance().deleteSnapMediaAndDoc(conversationId, message.getId(), message.getId(), message.getMediaUri(), new SnapRepository.SnapActionCallback() {
                    @Override
                    public void onSuccess() {
                        authorizedSnapMap.remove(message.getId());
                        renderPostsGridForCurrentTab();
                        showToast("Snap deleted");
                    }

                    @Override
                    public void onError(Exception e) {
                        showToast("Failed to delete snap");
                    }
                });
            }
        });
        dialog.show(getParentFragmentManager(), "SnapOptionsDialog");
    }

    public static class ProfileSnapMessageHolder {
        public final ChatMessage message;
        public final long createdAtMs;

        public ProfileSnapMessageHolder(ChatMessage message, long createdAtMs) {
            this.message = message;
            this.createdAtMs = createdAtMs;
        }
    }

    public static class ProfileSnapCardItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_SNAP_INCOMING = 1;
        public static final int TYPE_SNAP_OUTGOING = 2;

        public final int itemType;
        public final String dateHeader;
        public final ChatMessage message;

        public ProfileSnapCardItem(String dateHeader) {
            this.itemType = TYPE_HEADER;
            this.dateHeader = dateHeader;
            this.message = null;
        }

        public ProfileSnapCardItem(ChatMessage message, int itemType) {
            this.itemType = itemType;
            this.dateHeader = null;
            this.message = message;
        }
    }

    private static class ProfileSnapCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ProfileSnapCardItem> items = new ArrayList<>();
        private final ChatMessageAdapter.OnSnapClickListener snapClickListener;
        private final ChatMessageAdapter.OnMessageLongClickListener snapLongClickListener;

        ProfileSnapCardAdapter(ChatMessageAdapter.OnSnapClickListener snapClickListener,
                               ChatMessageAdapter.OnMessageLongClickListener snapLongClickListener) {
            this.snapClickListener = snapClickListener;
            this.snapLongClickListener = snapLongClickListener;
        }

        public void setItems(List<ProfileSnapCardItem> newItems) {
            this.items.clear();
            if (newItems != null) this.items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).itemType;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == ProfileSnapCardItem.TYPE_HEADER) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                tv.setPadding(32, 20, 32, 10);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                tv.setLetterSpacing(0.06f);
                tv.setTextColor(Color.parseColor("#94A3B8"));
                tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                return new HeaderViewHolder(tv);
            } else if (viewType == ProfileSnapCardItem.TYPE_SNAP_INCOMING) {
                View v = inflater.inflate(R.layout.item_message_snap_incoming, parent, false);
                return new ChatMessageAdapter.SnapMessageViewHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_message_snap_outgoing, parent, false);
                return new ChatMessageAdapter.SnapMessageViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ProfileSnapCardItem item = items.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvHeader.setText(item.dateHeader);
            } else if (holder instanceof ChatMessageAdapter.SnapMessageViewHolder) {
                ChatMessageAdapter.SnapMessageViewHolder vh = (ChatMessageAdapter.SnapMessageViewHolder) holder;
                ChatMessage message = item.message;
                if (message == null) return;

                boolean isVideo = message.getSnapKind() == ChatMessage.SnapKind.VIDEO;
                vh.tvSnapLabel.setText(isVideo ? "Video Snap" : "Snap");
                vh.ivSnapIcon.setImageResource(isVideo ? R.drawable.ic_snap_video : R.drawable.ic_nav_camera);

                if (message.isExpired()) {
                    vh.tvSnapSubtext.setText("Snap Expired");
                    vh.cardSnapBubble.setAlpha(0.5f);
                } else if (message.isSavedInChat()) {
                    vh.tvSnapSubtext.setText("Saved in Chat • Hold for options");
                    vh.cardSnapBubble.setAlpha(1.0f);
                } else if (message.isOutgoing()) {
                    if (message.getStatus() == ChatMessage.Status.READ) {
                        vh.tvSnapSubtext.setText("Opened • " + message.getDurationText());
                    } else if (message.getStatus() == ChatMessage.Status.DELIVERED) {
                        vh.tvSnapSubtext.setText("Delivered • " + message.getDurationText());
                    } else {
                        vh.tvSnapSubtext.setText("Sent • " + message.getDurationText());
                    }
                    vh.cardSnapBubble.setAlpha(1.0f);
                } else {
                    if (message.isViewed()) {
                        int remaining = message.getRemainingReplays();
                        if (remaining > 0) {
                            vh.tvSnapSubtext.setText("Opened • Hold to replay (" + remaining + "/3)");
                        } else {
                            vh.tvSnapSubtext.setText("Opened • 0 replays left");
                        }
                    } else {
                        vh.tvSnapSubtext.setText("Tap to view • " + message.getDurationText());
                    }
                    vh.cardSnapBubble.setAlpha(1.0f);
                }

                vh.tvMessageTime.setText(message.getTimestamp());
                if (vh.tvIncomingAvatar != null) {
                    vh.tvIncomingAvatar.setText(message.getSenderName() != null && message.getSenderName().length() >= 2 ? message.getSenderName().substring(0, 2).toUpperCase() : "GB");
                    vh.tvIncomingAvatar.setBackgroundResource(R.drawable.bg_avatar_gradient_3);
                }

                vh.cardSnapBubble.setOnClickListener(v -> {
                    if (snapClickListener != null) {
                        snapClickListener.onSnapClick(message);
                    }
                });

                vh.cardSnapBubble.setOnLongClickListener(v -> {
                    if (snapLongClickListener != null) {
                        snapLongClickListener.onMessageLongClick(message, v);
                        return true;
                    }
                    return false;
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvHeader;
            HeaderViewHolder(View itemView) {
                super(itemView);
                tvHeader = (TextView) itemView;
            }
        }
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    // Profile Post Data Model
    public static class ProfilePostItem {
        public final String id;
        public final String title;
        public final String caption;
        public final String mediaUrl;
        public final List<String> mediaUrls;
        public final String mediaType;
        public final long createdAtMs;
        public final boolean isSnap;

        public ProfilePostItem(String id, String title, String caption, String mediaUrl, List<String> mediaUrls, String mediaType, long createdAtMs, boolean isSnap) {
            this.id = id;
            this.title = title;
            this.caption = caption;
            this.mediaUrl = mediaUrl;
            this.mediaUrls = mediaUrls != null && !mediaUrls.isEmpty() ? mediaUrls : (mediaUrl != null ? java.util.Collections.singletonList(mediaUrl) : new java.util.ArrayList<>());
            this.mediaType = mediaType;
            this.createdAtMs = createdAtMs;
            this.isSnap = isSnap;
        }

        public ProfilePostItem(String id, String title, String caption, String mediaUrl, String mediaType, long createdAtMs, boolean isSnap) {
            this(id, title, caption, mediaUrl, null, mediaType, createdAtMs, isSnap);
        }

        public ProfilePostItem(String id, String title, String caption, String mediaUrl, String mediaType, long createdAtMs) {
            this(id, title, caption, mediaUrl, null, mediaType, createdAtMs, "SNAP".equalsIgnoreCase(mediaType));
        }
    }

    private interface OnPostClickListener {
        void onPostClick(ProfilePostItem item);
    }

    private static class PostCarouselAdapter extends RecyclerView.Adapter<PostCarouselAdapter.CarouselViewHolder> {
        private final List<String> imageUrls;

        PostCarouselAdapter(List<String> imageUrls) {
            this.imageUrls = imageUrls != null ? imageUrls : new java.util.ArrayList<>();
        }

        @NonNull
        @Override
        public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post_carousel_image, parent, false);
            return new CarouselViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
            holder.bind(imageUrls.get(position));
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        static class CarouselViewHolder extends RecyclerView.ViewHolder {
            private final ImageView ivCarouselImage;

            CarouselViewHolder(@NonNull View itemView) {
                super(itemView);
                ivCarouselImage = itemView.findViewById(R.id.iv_carousel_image);
            }

            void bind(String url) {
                if (ivCarouselImage != null && !TextUtils.isEmpty(url)) {
                    Glide.with(itemView.getContext())
                            .load(url)
                            .placeholder(R.drawable.bg_sample_gym_preview)
                            .error(R.drawable.bg_sample_gym_preview)
                            .into(ivCarouselImage);
                }
            }
        }
    }

    // RecyclerView Adapter
    private static class ProfilePostAdapter extends RecyclerView.Adapter<ProfilePostAdapter.PostViewHolder> {

        private final List<ProfilePostItem> list;
        private final OnPostClickListener listener;

        ProfilePostAdapter(List<ProfilePostItem> list, OnPostClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_post_grid, parent, false);
            int columnWidth = parent.getWidth() / 3;
            if (columnWidth <= 0) {
                columnWidth = parent.getResources().getDisplayMetrics().widthPixels / 3;
            }
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.width = columnWidth;
                lp.height = columnWidth;
                view.setLayoutParams(lp);
            }
            return new PostViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            ProfilePostItem item = list.get(position);
            holder.bind(item, listener);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class PostViewHolder extends RecyclerView.ViewHolder {
            ImageView ivThumbnail;
            ImageView ivMediaTypeIcon;
            TextView tvGridDateTime;

            PostViewHolder(@NonNull View itemView) {
                super(itemView);
                ivThumbnail = itemView.findViewById(R.id.iv_grid_thumbnail);
                ivMediaTypeIcon = itemView.findViewById(R.id.iv_media_type_icon);
                tvGridDateTime = itemView.findViewById(R.id.tv_grid_date_time);
            }

            void bind(ProfilePostItem item, OnPostClickListener listener) {
                if (ivThumbnail != null && item.mediaUrl != null) {
                    Glide.with(itemView.getContext())
                            .load(item.mediaUrl)
                            .placeholder(R.drawable.bg_sample_gym_preview)
                            .error(R.drawable.bg_sample_gym_preview)
                            .into(ivThumbnail);
                }

                if (ivMediaTypeIcon != null) {
                    if (item.mediaUrls != null && item.mediaUrls.size() > 1) {
                        ivMediaTypeIcon.setImageResource(R.drawable.ic_carousel_layers);
                        ivMediaTypeIcon.setVisibility(View.VISIBLE);
                    } else if ("VIDEO".equalsIgnoreCase(item.mediaType) || "SNAP".equalsIgnoreCase(item.mediaType)) {
                        ivMediaTypeIcon.setImageResource(R.drawable.ic_snap_video);
                        ivMediaTypeIcon.setVisibility(View.VISIBLE);
                    } else {
                        ivMediaTypeIcon.setVisibility(View.GONE);
                    }
                }

                if (tvGridDateTime != null) {
                    if (item.isSnap && item.createdAtMs > 0) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault());
                        tvGridDateTime.setText(sdf.format(new java.util.Date(item.createdAtMs)));
                        tvGridDateTime.setVisibility(View.VISIBLE);
                    } else {
                        tvGridDateTime.setVisibility(View.GONE);
                    }
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onPostClick(item);
                });
            }
        }
    }
}
