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
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;

import com.amstudio.fitup.model.StreakStatus;
import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.StreakRepository;
import com.amstudio.fitup.repository.TencentChatManager;
import com.amstudio.fitup.repository.UserRepository;
import com.amstudio.fitup.util.AvatarLoader;
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

    private TextView tvStatPostsValue;
    private TextView tvStatFollowersValue;
    private TextView tvStatFollowingValue;

    private ListenerRegistration userProfileRegistration;
    private ListenerRegistration postsRegistration;
    private ListenerRegistration followersRegistration;
    private ListenerRegistration followingRegistration;

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
    private View containerEmptyState;

    // Posts Grid Views
    private RecyclerView rvPostsGrid;
    private final List<ProfilePostItem> allUserPosts = new ArrayList<>();
    private final List<ProfilePostItem> currentTabPosts = new ArrayList<>();
    private ProfilePostAdapter gridAdapter;
    private TabState currentTabState = TabState.POSTS;

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
                if (statusBarTop > 0) {
                    topBar.setPadding(
                            topBar.getPaddingLeft(),
                            statusBarTop,
                            topBar.getPaddingRight(),
                            topBar.getPaddingBottom()
                    );
                }
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
        containerEmptyState = view.findViewById(R.id.container_empty_state);

        rvPostsGrid = view.findViewById(R.id.rv_profile_posts_grid);
        setupPostsGridRecyclerView();
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
                FollowersListDialog dialog = FollowersListDialog.newInstance("Followers");
                dialog.show(getParentFragmentManager(), "FollowersListDialog");
            });
        }

        View statFollowing = view.findViewById(R.id.stat_following_container);
        if (statFollowing != null) {
            statFollowing.setOnClickListener(v -> {
                FollowersListDialog dialog = FollowersListDialog.newInstance("Following");
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
            PostDetailActivity.open(
                    getContext(),
                    item.id,
                    item.mediaUrl,
                    item.title,
                    item.caption,
                    currentUid
            );
        });

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
                .setMessage("Are you sure you want to log out of FitUp?")
                .setPositiveButton("Log Out", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        UserRepository.getInstance().updateUserOnlineStatus(false);
        TencentChatManager.getInstance().logout(null);
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
                    }
                });

        // 2. Real-Time Posts/Snaps Count & Grid Listener
        postsRegistration = db.collection("snaps")
                .whereEqualTo("senderUid", currentUid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !isAdded()) return;

                    allUserPosts.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String id = doc.getString("id");
                        String title = doc.getString("title");
                        String caption = doc.getString("caption");
                        String mediaUrl = doc.getString("mediaUrl");
                        String mediaType = doc.getString("mediaType");
                        Timestamp ts = doc.getTimestamp("createdAt");
                        long createdAtMs = ts != null ? ts.toDate().getTime() : System.currentTimeMillis();

                        if (mediaUrl != null && !mediaUrl.isEmpty()) {
                            allUserPosts.add(new ProfilePostItem(id, title, caption, mediaUrl, mediaType, createdAtMs));
                        }
                    }

                    // Sort newest posts first
                    allUserPosts.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));

                    if (tvStatPostsValue != null) {
                        tvStatPostsValue.setText(String.valueOf(allUserPosts.size()));
                    }

                    renderPostsGridForCurrentTab();
                });

        // 3. Real-Time Followers Count Listener (User is user2 in friendship)
        followersRegistration = db.collection("friendships")
                .whereEqualTo("user2Uid", currentUid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !isAdded()) return;
                    int count = snapshot.size();
                    if (tvStatFollowersValue != null) {
                        tvStatFollowersValue.setText(String.valueOf(count));
                    }
                });

        // 4. Real-Time Following Count Listener (User is user1 in friendship)
        followingRegistration = db.collection("friendships")
                .whereEqualTo("user1Uid", currentUid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !isAdded()) return;
                    int count = snapshot.size();
                    if (tvStatFollowingValue != null) {
                        tvStatFollowingValue.setText(String.valueOf(count));
                    }
                });
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
        if (followersRegistration != null) {
            followersRegistration.remove();
            followersRegistration = null;
        }
        if (followingRegistration != null) {
            followingRegistration.remove();
            followingRegistration = null;
        }
    }

    private void renderPostsGridForCurrentTab() {
        currentTabPosts.clear();

        for (ProfilePostItem item : allUserPosts) {
            switch (currentTabState) {
                case SNAPS:
                    if ("VIDEO".equalsIgnoreCase(item.mediaType)) {
                        currentTabPosts.add(item);
                    }
                    break;
                case ACTIVITY:
                    if ("ACTIVITY".equalsIgnoreCase(item.mediaType)) {
                        currentTabPosts.add(item);
                    }
                    break;
                case POSTS:
                default:
                    if (!"VIDEO".equalsIgnoreCase(item.mediaType) && !"ACTIVITY".equalsIgnoreCase(item.mediaType)) {
                        currentTabPosts.add(item);
                    }
                    break;
            }
        }

        // If POSTS tab filtered list is empty but user has posts, show all user posts in main POSTS grid (Instagram behavior)
        if (currentTabState == TabState.POSTS && currentTabPosts.isEmpty() && !allUserPosts.isEmpty()) {
            currentTabPosts.addAll(allUserPosts);
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
        public final String mediaType;
        public final long createdAtMs;

        public ProfilePostItem(String id, String title, String caption, String mediaUrl, String mediaType, long createdAtMs) {
            this.id = id;
            this.title = title;
            this.caption = caption;
            this.mediaUrl = mediaUrl;
            this.mediaType = mediaType;
            this.createdAtMs = createdAtMs;
        }
    }

    // RecyclerView Adapter
    private static class ProfilePostAdapter extends RecyclerView.Adapter<ProfilePostAdapter.PostViewHolder> {

        interface OnPostClickListener {
            void onPostClick(ProfilePostItem item);
        }

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

            PostViewHolder(@NonNull View itemView) {
                super(itemView);
                ivThumbnail = itemView.findViewById(R.id.iv_grid_thumbnail);
                ivMediaTypeIcon = itemView.findViewById(R.id.iv_media_type_icon);
            }

            void bind(ProfilePostItem item, OnPostClickListener listener) {
                if (ivThumbnail != null && item.mediaUrl != null) {
                    Glide.with(itemView.getContext())
                            .load(item.mediaUrl)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .error(R.drawable.ic_avatar_placeholder)
                            .into(ivThumbnail);
                }

                if (ivMediaTypeIcon != null) {
                    ivMediaTypeIcon.setVisibility("VIDEO".equalsIgnoreCase(item.mediaType) ? View.VISIBLE : View.GONE);
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onPostClick(item);
                });
            }
        }
    }
}
