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

    private ImageButton btnClose;
    private TextView tvTitle;
    private EditText etSearch;
    private RecyclerView rvFollowers;
    private View layoutEmpty;

    private String title = "Followers & Friends";
    private FollowersAdapter adapter;
    private final List<UserProfile> fullFollowersList = new ArrayList<>();
    private final List<UserProfile> filteredList = new ArrayList<>();

    public static FollowersListDialog newInstance() {
        return newInstance("Followers & Friends");
    }

    public static FollowersListDialog newInstance(String dialogTitle) {
        FollowersListDialog dialog = new FollowersListDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, dialogTitle);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (getArguments() != null) {
            String argTitle = getArguments().getString(ARG_TITLE);
            if (!TextUtils.isEmpty(argTitle)) {
                title = argTitle;
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

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

        if (tvTitle != null) {
            tvTitle.setText(title);
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
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
        FriendRepository.getInstance().getAcceptedFriends(new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(List<UserProfile> friends) {
                if (isAdded()) {
                    fullFollowersList.clear();
                    if (friends != null && !friends.isEmpty()) {
                        fullFollowersList.addAll(friends);
                        filterFollowers(etSearch != null ? etSearch.getText().toString() : "");
                    } else {
                        // If no direct friends, fallback search to show active members
                        FriendRepository.getInstance().searchUsersByUsername("a", new FriendRepository.FriendsListCallback() {
                            @Override
                            public void onSuccess(List<UserProfile> allUsers) {
                                if (isAdded()) {
                                    fullFollowersList.clear();
                                    if (allUsers != null) {
                                        fullFollowersList.addAll(allUsers);
                                    }
                                    filterFollowers(etSearch != null ? etSearch.getText().toString() : "");
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                if (isAdded()) {
                                    filterFollowers("");
                                }
                            }
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
        });
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

        FollowersAdapter(List<UserProfile> list, OnFollowerClickListener listener) {
            this.list = list;
            this.listener = listener;
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
            holder.bind(user, listener);
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

            void bind(UserProfile user, OnFollowerClickListener listener) {
                if (tvName != null) {
                    tvName.setText(!TextUtils.isEmpty(user.getDisplayName()) ? user.getDisplayName() : "FitUp Member");
                }
                if (tvUsername != null) {
                    tvUsername.setText(!TextUtils.isEmpty(user.getUsername()) ? "@" + user.getUsername() : "");
                }
                if (btnAction != null) {
                    btnAction.setVisibility(View.GONE);
                }
                if (ivAvatar != null) {
                    AvatarLoader.loadAvatar(itemView.getContext(), user.getAvatarUrl(), ivAvatar);
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
