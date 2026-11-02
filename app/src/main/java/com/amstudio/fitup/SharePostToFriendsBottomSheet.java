package com.amstudio.fitup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.FriendRepository;
import com.amstudio.fitup.repository.UserRepository;
import com.amstudio.fitup.util.AvatarLoader;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SharePostToFriendsBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "SharePostToFriends";

    private static final String ARG_POST_ID = "arg_post_id";
    private static final String ARG_MEDIA_URL = "arg_media_url";
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_CAPTION = "arg_caption";

    private String postId;
    private String mediaUrl;
    private String title;
    private String caption;

    private EditText etSearch;
    private RecyclerView rvFriends;
    private TextView tvEmptyFriends;

    private final List<UserProfile> friendsList = new ArrayList<>();
    private final Set<String> sentUserIds = new HashSet<>();
    private ShareFriendsAdapter adapter;

    public static SharePostToFriendsBottomSheet newInstance(String postId, String mediaUrl, String title, String caption) {
        SharePostToFriendsBottomSheet fragment = new SharePostToFriendsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        args.putString(ARG_MEDIA_URL, mediaUrl);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_CAPTION, caption);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            postId = getArguments().getString(ARG_POST_ID);
            mediaUrl = getArguments().getString(ARG_MEDIA_URL);
            title = getArguments().getString(ARG_TITLE);
            caption = getArguments().getString(ARG_CAPTION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_share_post_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etSearch = view.findViewById(R.id.et_search_share_friends);
        rvFriends = view.findViewById(R.id.rv_share_friends_list);
        tvEmptyFriends = view.findViewById(R.id.tv_empty_share_friends);

        adapter = new ShareFriendsAdapter(friendsList, this::sendPostToFriend);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFriends.setAdapter(adapter);

        setupSearchListener();
        loadFriends();
    }

    private void setupSearchListener() {
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (adapter != null) {
                        adapter.filter(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void loadFriends() {
        FriendRepository.getInstance().getAcceptedFriends(new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(List<UserProfile> friends) {
                if (!isAdded()) return;
                friendsList.clear();
                if (friends != null && !friends.isEmpty()) {
                    friendsList.addAll(friends);
                }
                adapter.setItems(friendsList);

                boolean hasFriends = !friendsList.isEmpty();
                rvFriends.setVisibility(hasFriends ? View.VISIBLE : View.GONE);
                tvEmptyFriends.setVisibility(hasFriends ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Log.e(TAG, "Error loading friends for sharing", e);
                rvFriends.setVisibility(View.GONE);
                tvEmptyFriends.setVisibility(View.VISIBLE);
            }
        });
    }

    private void sendPostToFriend(UserProfile targetUser, MaterialButton btnSend) {
        if (targetUser == null || TextUtils.isEmpty(targetUser.getUid())) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) {
            Toast.makeText(getContext(), "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String recipientUid = targetUser.getUid();
        String conversationId = currentUid.compareTo(recipientUid) < 0 ?
                currentUid + "_" + recipientUid : recipientUid + "_" + currentUid;

        btnSend.setEnabled(false);
        btnSend.setText("Sending...");

        String msgId = "post_share_" + System.currentTimeMillis();
        String postTitle = !TextUtils.isEmpty(title) ? title : (!TextUtils.isEmpty(caption) ? caption : "Shared Post");

        Map<String, Object> msgDoc = new HashMap<>();
        msgDoc.put("id", msgId);
        msgDoc.put("senderUid", currentUid);
        msgDoc.put("senderName", "You");
        msgDoc.put("text", postTitle);
        msgDoc.put("type", "POST_SHARE");
        msgDoc.put("postId", postId);
        msgDoc.put("mediaUrl", mediaUrl);
        msgDoc.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("chats").document(conversationId)
                .collection("messages").document(msgId)
                .set(msgDoc)
                .addOnSuccessListener(aVoid -> {
                    sentUserIds.add(recipientUid);
                    btnSend.setText("Sent ✓");
                    btnSend.setEnabled(false);
                    btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FFFFFF")));

                    String name = !TextUtils.isEmpty(targetUser.getDisplayName()) ?
                            targetUser.getDisplayName() : targetUser.getUsername();
                    Toast.makeText(getContext(), "Post sent to " + (name != null ? name : "friend") + "!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    btnSend.setEnabled(true);
                    btnSend.setText("Send");
                    Toast.makeText(getContext(), "Failed to send post", Toast.LENGTH_SHORT).show();
                });
    }

    private interface OnFriendSendClickListener {
        void onSendClick(UserProfile user, MaterialButton btnSend);
    }

    private class ShareFriendsAdapter extends RecyclerView.Adapter<ShareFriendsAdapter.ShareViewHolder> {

        private final List<UserProfile> originalList = new ArrayList<>();
        private final List<UserProfile> filteredList = new ArrayList<>();
        private final OnFriendSendClickListener listener;

        ShareFriendsAdapter(List<UserProfile> list, OnFriendSendClickListener listener) {
            this.listener = listener;
            setItems(list);
        }

        void setItems(List<UserProfile> items) {
            originalList.clear();
            if (items != null) originalList.addAll(items);
            filter(etSearch != null ? etSearch.getText().toString() : "");
        }

        void filter(String query) {
            filteredList.clear();
            if (TextUtils.isEmpty(query)) {
                filteredList.addAll(originalList);
            } else {
                String lower = query.toLowerCase(Locale.ROOT).trim();
                for (UserProfile user : originalList) {
                    String name = user.getDisplayName() != null ? user.getDisplayName().toLowerCase(Locale.ROOT) : "";
                    String uname = user.getUsername() != null ? user.getUsername().toLowerCase(Locale.ROOT) : "";
                    if (name.contains(lower) || uname.contains(lower)) {
                        filteredList.add(user);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ShareViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_share_friend, parent, false);
            return new ShareViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ShareViewHolder holder, int position) {
            UserProfile user = filteredList.get(position);
            holder.bind(user, listener);
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class ShareViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView ivAvatar;
            TextView tvName;
            TextView tvUsername;
            MaterialButton btnSend;

            ShareViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_share_friend_avatar);
                tvName = itemView.findViewById(R.id.tv_share_friend_name);
                tvUsername = itemView.findViewById(R.id.tv_share_friend_username);
                btnSend = itemView.findViewById(R.id.btn_send_post_to_friend);
            }

            void bind(UserProfile user, OnFriendSendClickListener listener) {
                String name = !TextUtils.isEmpty(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
                tvName.setText(name != null ? name : "Friend");

                String uname = !TextUtils.isEmpty(user.getUsername()) ? "@" + user.getUsername() : "";
                tvUsername.setText(uname);

                AvatarLoader.loadAvatar(itemView.getContext(), user.getAvatarUrl(), ivAvatar);

                boolean alreadySent = user.getUid() != null && sentUserIds.contains(user.getUid());
                if (alreadySent) {
                    btnSend.setText("Sent ✓");
                    btnSend.setEnabled(false);
                    btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FFFFFF")));
                } else {
                    btnSend.setText("Send");
                    btnSend.setEnabled(true);
                    btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3897F0")));
                }

                btnSend.setOnClickListener(v -> {
                    if (listener != null) listener.onSendClick(user, btnSend);
                });
            }
        }
    }
}