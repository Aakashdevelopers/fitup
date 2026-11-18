package com.amstudio.fitbond;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.FriendRepository;
import com.amstudio.fitbond.util.AvatarLoader;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class FollowersListDialog extends DialogFragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_TARGET_UID = "arg_target_uid";

    private ImageButton btnClose;
    private TextView tvTitle;
    private EditText etSearch;
    private RecyclerView rvFollowers;
    private View layoutEmpty;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshFollowers;

    private View tabFollowers;
    private View tabFollowing;
    private TextView tvTabFollowersLabel;
    private TextView tvTabFollowingLabel;
    private View indicatorTabFollowers;
    private View indicatorTabFollowing;
    private boolean isShowingFollowers = true;

    private String targetUid;
    private String title = "Followers & Friends";
    private FollowersAdapter adapter;
    private final List<UserProfile> fullFollowersList = new ArrayList<>();
    private final List<UserProfile> filteredList = new ArrayList<>();

    public static FollowersListDialog newInstance() {
        return newInstance(null, "Followers");
    }

    public static FollowersListDialog newInstance(String dialogTitle) {
        return newInstance(null, dialogTitle);
    }

    public static FollowersListDialog newInstance(String targetUid, String dialogTitle) {
        FollowersListDialog dialog = new FollowersListDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_UID, targetUid);
        args.putString(ARG_TITLE, dialogTitle);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (getArguments() != null) {
            targetUid = getArguments().getString(ARG_TARGET_UID);
            String argTitle = getArguments().getString(ARG_TITLE);
            if (!TextUtils.isEmpty(argTitle)) {
                title = argTitle;
                if ("Following".equalsIgnoreCase(argTitle)) {
                    isShowingFollowers = false;
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_followers_list, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusBarTop = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
            float density = getResources().getDisplayMetrics().density;
            int topPadding = Math.max(statusBarTop, Math.round(24 * density)) + Math.round(12 * density);
            v.setPadding(
                    v.getPaddingLeft(),
                    topPadding,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets;
        });
        view.post(() -> androidx.core.view.ViewCompat.requestApplyInsets(view));

        initViews(view);
        setupRecyclerView();
        setupSearch();
        loadFollowers();
    }

    private void initViews(View view) {
        btnClose = view.findViewById(R.id.btn_close_followers);
        tvTitle = view.findViewById(R.id.tv_followers_title);
        etSearch = view.findViewById(R.id.et_search_followers);
        rvFollowers = view.findViewById(R.id.rv_followers_list);
        layoutEmpty = view.findViewById(R.id.layout_empty_followers);

        tabFollowers = view.findViewById(R.id.tab_followers_list);
        tabFollowing = view.findViewById(R.id.tab_following_list);
        tvTabFollowersLabel = view.findViewById(R.id.tv_tab_followers_label);
        tvTabFollowingLabel = view.findViewById(R.id.tv_tab_following_label);
        indicatorTabFollowers = view.findViewById(R.id.indicator_tab_followers_list);
        indicatorTabFollowing = view.findViewById(R.id.indicator_tab_following_list);

        if (tabFollowers != null) {
            tabFollowers.setOnClickListener(v -> switchTab(true));
        }
        if (tabFollowing != null) {
            tabFollowing.setOnClickListener(v -> switchTab(false));
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        swipeRefreshFollowers = view.findViewById(R.id.swipe_refresh_followers);
        if (swipeRefreshFollowers != null) {
            swipeRefreshFollowers.setColorSchemeResources(R.color.electric_blue, R.color.primary_blue);
            swipeRefreshFollowers.setOnRefreshListener(() -> {
                FriendRepository.getInstance().invalidateFriendsCache();
                loadFollowers();
                swipeRefreshFollowers.postDelayed(() -> {
                    if (swipeRefreshFollowers != null) {
                        swipeRefreshFollowers.setRefreshing(false);
                    }
                }, 800);
            });
        }

        updateTabUI();
    }

    private void switchTab(boolean showFollowers) {
        if (isShowingFollowers == showFollowers) return;
        isShowingFollowers = showFollowers;
        title = isShowingFollowers ? "Followers" : "Following";
        updateTabUI();
        loadFollowers();
    }

    private void updateTabUI() {
        if (getContext() == null) return;
        int activeColor = androidx.core.content.ContextCompat.getColor(getContext(), R.color.electric_blue);
        int inactiveColor = androidx.core.content.ContextCompat.getColor(getContext(), R.color.secondary_text);

        if (isShowingFollowers) {
            if (tvTabFollowersLabel != null) tvTabFollowersLabel.setTextColor(activeColor);
            if (indicatorTabFollowers != null) indicatorTabFollowers.setVisibility(View.VISIBLE);
            if (tvTabFollowingLabel != null) tvTabFollowingLabel.setTextColor(inactiveColor);
            if (indicatorTabFollowing != null) indicatorTabFollowing.setVisibility(View.INVISIBLE);
            if (etSearch != null) etSearch.setHint("Search followers...");
        } else {
            if (tvTabFollowingLabel != null) tvTabFollowingLabel.setTextColor(activeColor);
            if (indicatorTabFollowing != null) indicatorTabFollowing.setVisibility(View.VISIBLE);
            if (tvTabFollowersLabel != null) tvTabFollowersLabel.setTextColor(inactiveColor);
            if (indicatorTabFollowers != null) indicatorTabFollowers.setVisibility(View.INVISIBLE);
            if (etSearch != null) etSearch.setHint("Search following...");
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null || rvFollowers == null) return;

        adapter = new FollowersAdapter(filteredList, user -> {
            if (user != null && user.getUid() != null) {
                UserProfileDetailsDialog dialog = UserProfileDetailsDialog.newInstance(user.getUid());
                dialog.show(getParentFragmentManager(), "UserProfileDetailsDialog");
            }
        });

        rvFollowers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFollowers.setAdapter(adapter);
    }

    private void setupSearch() {
        if (etSearch == null) return;

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFollowers(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadFollowers() {
        String uidToLoad = targetUid;
        if (TextUtils.isEmpty(uidToLoad)) {
            uidToLoad = com.amstudio.fitbond.repository.UserRepository.getInstance().getCurrentUid();
        }
        if (uidToLoad == null) return;

        String currentUid = com.amstudio.fitbond.repository.UserRepository.getInstance().getCurrentUid();

        FriendRepository.FriendsListCallback callback = new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(List<UserProfile> friends) {
                if (isAdded()) {
                    fullFollowersList.clear();
                    if (friends != null && !friends.isEmpty()) {
                        fullFollowersList.addAll(friends);
                    }
                    filterFollowers(etSearch != null ? etSearch.getText().toString() : "");

                    if (currentUid != null) {
                        FriendRepository.getInstance().getFollowing(currentUid, new FriendRepository.FriendsListCallback() {
                            @Override
                            public void onSuccess(List<UserProfile> myFollowing) {
                                if (isAdded() && adapter != null) {
                                    java.util.Set<String> uids = new java.util.HashSet<>();
                                    if (myFollowing != null) {
                                        for (UserProfile u : myFollowing) {
                                            if (u != null && u.getUid() != null) uids.add(u.getUid());
                                        }
                                    }
                                    adapter.setMyFollowingUids(uids);
                                }
                            }

                            @Override
                            public void onError(Exception e) {}
                        });
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded()) {
                    updateEmptyState();
                }
            }
        };

        if (isShowingFollowers) {
            FriendRepository.getInstance().getFollowers(uidToLoad, callback);
        } else {
            FriendRepository.getInstance().getFollowing(uidToLoad, callback);
        }
    }

    private void filterFollowers(String query) {
        filteredList.clear();
        String q = query != null ? query.toLowerCase().trim() : "";

        if (TextUtils.isEmpty(q)) {
            filteredList.addAll(fullFollowersList);
        } else {
            for (UserProfile user : fullFollowersList) {
                String name = user.getDisplayName() != null ? user.getDisplayName().toLowerCase() : "";
                String username = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
                if (name.contains(q) || username.contains(q)) {
                    filteredList.add(user);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        if (layoutEmpty != null) {
            if (filteredList.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                layoutEmpty.setVisibility(View.GONE);
            }
        }
    }

    // Adapter for Followers
    private static class FollowersAdapter extends RecyclerView.Adapter<FollowersAdapter.FollowerViewHolder> {

        interface OnFollowerClickListener {
            void onFollowerClick(UserProfile user);
        }

        private final List<UserProfile> list;
        private final OnFollowerClickListener listener;
        private final java.util.Set<String> myFollowingUids = new java.util.HashSet<>();

        FollowersAdapter(List<UserProfile> list, OnFollowerClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        public void setMyFollowingUids(java.util.Set<String> uids) {
            this.myFollowingUids.clear();
            if (uids != null) {
                this.myFollowingUids.addAll(uids);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FollowerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
            return new FollowerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FollowerViewHolder holder, int position) {
            UserProfile user = list.get(position);
            holder.bind(user, myFollowingUids, listener, this);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class FollowerViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            TextView tvUsername;
            View btnAction;

            FollowerViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_user_search_avatar);
                tvName = itemView.findViewById(R.id.tv_user_search_name);
                tvUsername = itemView.findViewById(R.id.tv_user_search_username);
                btnAction = itemView.findViewById(R.id.btn_user_add_friend);
            }

            void bind(UserProfile user, java.util.Set<String> myFollowingUids, OnFollowerClickListener listener, RecyclerView.Adapter<?> adapter) {
                if (tvName != null) {
                    tvName.setText(!TextUtils.isEmpty(user.getDisplayName()) ? user.getDisplayName() : "FitUp Member");
                }
                if (tvUsername != null) {
                    tvUsername.setText(!TextUtils.isEmpty(user.getUsername()) ? "@" + user.getUsername() : "");
                }
                if (ivAvatar != null) {
                    AvatarLoader.loadAvatar(itemView.getContext(), user.getAvatarUrl(), ivAvatar);
                }

                String currentUid = com.amstudio.fitbond.repository.UserRepository.getInstance().getCurrentUid();
                if (user.getUid() != null && user.getUid().equals(currentUid)) {
                    if (btnAction != null) btnAction.setVisibility(View.GONE);
                } else if (btnAction != null) {
                    btnAction.setVisibility(View.VISIBLE);
                    boolean isFollowing = myFollowingUids.contains(user.getUid());

                    if (btnAction instanceof ImageButton) {
                        ImageButton ib = (ImageButton) btnAction;
                        if (isFollowing) {
                            ib.setImageResource(R.drawable.ic_check);
                            ib.setBackgroundResource(R.drawable.bg_control_circle);
                        } else {
                            ib.setImageResource(R.drawable.ic_plus);
                            ib.setBackgroundResource(R.drawable.bg_circle_action_white);
                        }
                    }

                    btnAction.setOnClickListener(v -> {
                        btnAction.setEnabled(false);
                        if (myFollowingUids.contains(user.getUid())) {
                            FriendRepository.getInstance().removeFriend(user.getUid(), new FriendRepository.OperationCallback() {
                                @Override
                                public void onSuccess() {
                                    btnAction.setEnabled(true);
                                    myFollowingUids.remove(user.getUid());
                                    if (adapter != null) adapter.notifyDataSetChanged();
                                }

                                @Override
                                public void onError(Exception e) {
                                    btnAction.setEnabled(true);
                                }
                            });
                        } else {
                            FriendRepository.getInstance().followUser(user.getUid(), new FriendRepository.FollowCallback() {
                                @Override
                                public void onSuccess(boolean isDirectFollow) {
                                    btnAction.setEnabled(true);
                                    myFollowingUids.add(user.getUid());
                                    if (adapter != null) adapter.notifyDataSetChanged();
                                }

                                @Override
                                public void onError(Exception e) {
                                    btnAction.setEnabled(true);
                                }
                            });
                        }
                    });
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onFollowerClick(user);
                    }
                });
            }
        }
    }
}
