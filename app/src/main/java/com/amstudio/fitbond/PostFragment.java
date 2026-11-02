package com.amstudio.fitbond;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.FriendRepository;
import com.amstudio.fitbond.repository.UserRepository;
import com.amstudio.fitbond.util.AvatarLoader;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PostFragment extends Fragment {

    private static final String TAG = "PostFragment";
    private static final String ARG_POST_ID = "arg_post_id";

    private ViewPager2 vpPosts;
    private View layoutEmptyPosts;
    private MaterialButton btnCreateFirstPost;
    private ImageButton btnActionAdd;
    private ImageButton btnBack;

    private PostsAdapter postsAdapter;
    private final List<PostItem> postList = new ArrayList<>();
    private ListenerRegistration feedPostsRegistration;
    private String initialPostId;
    private boolean hasScrolledToInitialPost = false;

    public static class PostItem {
        public String id;
        public String title;
        public String caption;
        public String mediaUrl;
        public String mediaType;
        public String senderUid;
        public long createdAtMs;

        public PostItem(String id, String title, String caption, String mediaUrl, String mediaType, String senderUid, long createdAtMs) {
            this.id = id;
            this.title = title;
            this.caption = caption;
            this.mediaUrl = mediaUrl;
            this.mediaType = mediaType;
            this.senderUid = senderUid;
            this.createdAtMs = createdAtMs;
        }
    }

    public static PostFragment newInstance() {
        return new PostFragment();
    }

    public static PostFragment newInstance(String postId) {
        PostFragment fragment = new PostFragment();
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialPostId = getArguments().getString(ARG_POST_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_post_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupViewPager();
        setupClickListeners();
        loadAllUserPosts();
    }

    private void initViews(View view) {
        vpPosts = view.findViewById(R.id.vp_posts);
        layoutEmptyPosts = view.findViewById(R.id.layout_empty_posts);
        btnCreateFirstPost = view.findViewById(R.id.btn_create_first_post);
        btnActionAdd = view.findViewById(R.id.btn_action_add);
        btnBack = view.findViewById(R.id.btn_back_post_detail);

        if (btnBack != null) {
            btnBack.setVisibility(View.GONE);
        }
    }

    private void setupViewPager() {
        if (vpPosts == null) return;
        vpPosts.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        postsAdapter = new PostsAdapter(postList);
        vpPosts.setAdapter(postsAdapter);
    }

    private void setupClickListeners() {
        View.OnClickListener openNewPostListener = v -> {
            if (getContext() != null) {
                Intent intent = new Intent(getContext(), NewPostActivity.class);
                startActivity(intent);
            }
        };

        if (btnActionAdd != null) {
            btnActionAdd.setOnClickListener(openNewPostListener);
        }

        if (btnCreateFirstPost != null) {
            btnCreateFirstPost.setOnClickListener(openNewPostListener);
        }
    }

    private void loadAllUserPosts() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (feedPostsRegistration != null) {
            feedPostsRegistration.remove();
        }

        feedPostsRegistration = db.collection("snaps")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !isAdded()) return;

                    postList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String id = doc.getId();
                        String title = doc.getString("title");
                        String caption = doc.getString("caption");
                        String mediaUrl = doc.getString("mediaUrl");
                        String mediaType = doc.getString("mediaType");
                        String senderUid = doc.getString("senderUid");
                        Timestamp ts = doc.getTimestamp("createdAt");
                        long createdAtMs = ts != null ? ts.toDate().getTime() : System.currentTimeMillis();

                        if (!TextUtils.isEmpty(mediaUrl)) {
                            postList.add(new PostItem(id, title, caption, mediaUrl, mediaType, senderUid, createdAtMs));
                        }
                    }

                    if (postsAdapter != null) {
                        postsAdapter.notifyDataSetChanged();
                    }

                    if (postList.isEmpty()) {
                        if (layoutEmptyPosts != null) layoutEmptyPosts.setVisibility(View.VISIBLE);
                        if (vpPosts != null) vpPosts.setVisibility(View.GONE);
                    } else {
                        if (layoutEmptyPosts != null) layoutEmptyPosts.setVisibility(View.GONE);
                        if (vpPosts != null) vpPosts.setVisibility(View.VISIBLE);

                        if (!hasScrolledToInitialPost && !TextUtils.isEmpty(initialPostId)) {
                            for (int i = 0; i < postList.size(); i++) {
                                if (initialPostId.equals(postList.get(i).id)) {
                                    vpPosts.setCurrentItem(i, false);
                                    hasScrolledToInitialPost = true;
                                    break;
                                }
                            }
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (feedPostsRegistration != null) {
            feedPostsRegistration.remove();
            feedPostsRegistration = null;
        }
    }

    // ViewPager2 Adapter for scrolling through all users' posts
    private class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostViewHolder> {

        private final List<PostItem> items;

        PostsAdapter(List<PostItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post_page, parent, false);
            return new PostViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public void onViewRecycled(@NonNull PostViewHolder holder) {
            super.onViewRecycled(holder);
            holder.unbind();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class PostViewHolder extends RecyclerView.ViewHolder {

            private final ImageView ivPostMedia;
            private final View actionLikeContainer;
            private final ImageView ivActionLike;
            private final TextView tvLikeCount;
            private final View actionCommentContainer;
            private final TextView tvCommentCount;
            private final ImageView ivActionShare;
            private final ImageView ivActionBookmark;
            private final View layoutCreatorInfo;
            private final ShapeableImageView ivCreatorAvatar;
            private final TextView tvCreatorUsername;
            private final MaterialButton btnFollowUser;
            private final TextView tvPostCaption;

            private PostItem currentItem;
            private boolean isLiked = false;
            private boolean isBookmarked = false;
            private boolean isFollowing = false;
            private boolean isPending = false;
            private boolean isFollowLoading = false;
            private int likeCount = 0;
            private int commentCount = 0;

            private ListenerRegistration postDocRegistration;
            private ListenerRegistration likesRegistration;
            private ListenerRegistration commentsRegistration;

            PostViewHolder(@NonNull View itemView) {
                super(itemView);
                ivPostMedia = itemView.findViewById(R.id.iv_post_media);
                actionLikeContainer = itemView.findViewById(R.id.action_like_container);
                ivActionLike = itemView.findViewById(R.id.iv_action_like);
                tvLikeCount = itemView.findViewById(R.id.tv_like_count);
                actionCommentContainer = itemView.findViewById(R.id.action_comment_container);
                tvCommentCount = itemView.findViewById(R.id.tv_comment_count);
                ivActionShare = itemView.findViewById(R.id.iv_action_share);
                ivActionBookmark = itemView.findViewById(R.id.iv_action_bookmark);
                layoutCreatorInfo = itemView.findViewById(R.id.layout_creator_info);
                ivCreatorAvatar = itemView.findViewById(R.id.iv_creator_avatar);
                tvCreatorUsername = itemView.findViewById(R.id.tv_creator_username);
                btnFollowUser = itemView.findViewById(R.id.btn_follow_user);
                tvPostCaption = itemView.findViewById(R.id.tv_post_caption);
            }

            void bind(PostItem item) {
                this.currentItem = item;
                unbind();

                if (ivPostMedia != null && !TextUtils.isEmpty(item.mediaUrl)) {
                    Glide.with(itemView.getContext())
                            .load(item.mediaUrl)
                            .placeholder(R.drawable.bg_sample_gym_preview)
                            .error(R.drawable.bg_sample_gym_preview)
                            .into(ivPostMedia);
                }

                updateCaptionUI(item.title, item.caption);
                loadCreatorProfile(item.senderUid);
                checkFollowStatus(item.senderUid);
                setupRealtimeListeners(item.id);
                setupClickListeners();
            }

            void unbind() {
                if (postDocRegistration != null) {
                    postDocRegistration.remove();
                    postDocRegistration = null;
                }
                if (likesRegistration != null) {
                    likesRegistration.remove();
                    likesRegistration = null;
                }
                if (commentsRegistration != null) {
                    commentsRegistration.remove();
                    commentsRegistration = null;
                }
            }

            private void setupClickListeners() {
                if (actionLikeContainer != null) {
                    actionLikeContainer.setOnClickListener(v -> toggleLikeState());
                }

                if (actionCommentContainer != null) {
                    actionCommentContainer.setOnClickListener(v -> openCommentBottomSheet());
                }

                if (ivActionShare != null) {
                    ivActionShare.setOnClickListener(v -> sharePostUrl());
                }

                if (ivActionBookmark != null) {
                    ivActionBookmark.setOnClickListener(v -> toggleBookmarkState());
                }

                if (btnFollowUser != null) {
                    btnFollowUser.setOnClickListener(v -> toggleFollowStatus());
                }

                View.OnClickListener profileListener = v -> openUserProfile();
                if (layoutCreatorInfo != null) layoutCreatorInfo.setOnClickListener(profileListener);
                if (ivCreatorAvatar != null) ivCreatorAvatar.setOnClickListener(profileListener);
                if (tvCreatorUsername != null) tvCreatorUsername.setOnClickListener(profileListener);
            }

            private void updateCaptionUI(String titleText, String captionText) {
                if (tvPostCaption == null) return;

                boolean hasTitle = !TextUtils.isEmpty(titleText);
                boolean hasCaption = !TextUtils.isEmpty(captionText);

                if (hasTitle && hasCaption && !titleText.trim().equalsIgnoreCase(captionText.trim())) {
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    ssb.append(titleText.trim(), new StyleSpan(Typeface.BOLD), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append("\n");
                    ssb.append(captionText.trim());
                    tvPostCaption.setText(ssb);
                    tvPostCaption.setVisibility(View.VISIBLE);
                } else if (hasTitle) {
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    ssb.append(titleText.trim(), new StyleSpan(Typeface.BOLD), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tvPostCaption.setText(ssb);
                    tvPostCaption.setVisibility(View.VISIBLE);
                } else if (hasCaption) {
                    tvPostCaption.setText(captionText.trim());
                    tvPostCaption.setVisibility(View.VISIBLE);
                } else {
                    tvPostCaption.setVisibility(View.GONE);
                }
            }

            private void loadCreatorProfile(String senderUid) {
                String currentUid = UserRepository.getInstance().getCurrentUid();
                if (!TextUtils.isEmpty(senderUid) && senderUid.equals(currentUid)) {
                    if (btnFollowUser != null) btnFollowUser.setVisibility(View.GONE);
                } else if (btnFollowUser != null) {
                    btnFollowUser.setVisibility(View.VISIBLE);
                }

                if (!TextUtils.isEmpty(senderUid)) {
                    UserRepository.getInstance().getUserProfile(senderUid, new UserRepository.ProfileCallback() {
                        @Override
                        public void onSuccess(UserProfile profile) {
                            if (profile == null) return;
                            if (tvCreatorUsername != null) {
                                String name = !TextUtils.isEmpty(profile.getUsername()) ?
                                        profile.getUsername() : profile.getDisplayName();
                                tvCreatorUsername.setText(name != null ? name : "user");
                            }
                            if (ivCreatorAvatar != null && isAdded()) {
                                AvatarLoader.loadAvatar(PostFragment.this, profile.getAvatarUrl(), ivCreatorAvatar);
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            if (tvCreatorUsername != null) tvCreatorUsername.setText("user");
                        }
                    });
                }
            }

            private void checkFollowStatus(String senderUid) {
                String currentUid = UserRepository.getInstance().getCurrentUid();
                if (currentUid == null || TextUtils.isEmpty(senderUid)) return;

                if (senderUid.equals(currentUid)) {
                    if (btnFollowUser != null) btnFollowUser.setVisibility(View.GONE);
                    return;
                }

                if (btnFollowUser != null) btnFollowUser.setVisibility(View.VISIBLE);

                FriendRepository.getInstance().getAcceptedFriends(new FriendRepository.FriendsListCallback() {
                    @Override
                    public void onSuccess(List<UserProfile> friends) {
                        boolean found = false;
                        if (friends != null) {
                            for (UserProfile friend : friends) {
                                if (friend != null && senderUid.equals(friend.getUid())) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (found) {
                            isFollowing = true;
                            isPending = false;
                            updateFollowButtonUI();
                        } else {
                            checkPendingRequestStatus(currentUid, senderUid);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        checkPendingRequestStatus(currentUid, senderUid);
                    }
                });
            }

            private void checkPendingRequestStatus(String currentUid, String targetUid) {
                FirebaseFirestore.getInstance().collection("friendRequests")
                        .whereEqualTo("senderUid", currentUid)
                        .whereEqualTo("receiverUid", targetUid)
                        .whereEqualTo("status", "PENDING")
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                isFollowing = false;
                                isPending = true;
                            } else {
                                isFollowing = false;
                                isPending = false;
                            }
                            updateFollowButtonUI();
                        })
                        .addOnFailureListener(e -> {
                            isFollowing = false;
                            isPending = false;
                            updateFollowButtonUI();
                        });
            }

            private void toggleFollowStatus() {
                if (currentItem == null || TextUtils.isEmpty(currentItem.senderUid) || isFollowLoading) return;
                String senderUid = currentItem.senderUid;

                String currentUid = UserRepository.getInstance().getCurrentUid();
                if (currentUid == null) {
                    Toast.makeText(itemView.getContext(), "Please sign in to follow users", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (senderUid.equals(currentUid)) return;

                isFollowLoading = true;
                if (btnFollowUser != null) btnFollowUser.setEnabled(false);

                if (isFollowing) {
                    FriendRepository.getInstance().removeFriend(senderUid, new FriendRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            isFollowLoading = false;
                            isFollowing = false;
                            isPending = false;
                            if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                            updateFollowButtonUI();
                            Toast.makeText(itemView.getContext(), "Unfollowed user", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            isFollowLoading = false;
                            if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                            Toast.makeText(itemView.getContext(), "Failed to unfollow", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (isPending) {
                    isFollowLoading = false;
                    if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                    Toast.makeText(itemView.getContext(), "Follow request pending", Toast.LENGTH_SHORT).show();
                } else {
                    FriendRepository.getInstance().sendFriendRequest(senderUid, new FriendRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            isFollowLoading = false;
                            isPending = true;
                            if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                            updateFollowButtonUI();
                            Toast.makeText(itemView.getContext(), "Follow request sent!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            isFollowLoading = false;
                            if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                            Toast.makeText(itemView.getContext(), "Failed to send follow request", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            private void updateFollowButtonUI() {
                if (btnFollowUser == null || currentItem == null) return;
                String senderUid = currentItem.senderUid;

                String currentUid = UserRepository.getInstance().getCurrentUid();
                if (!TextUtils.isEmpty(senderUid) && senderUid.equals(currentUid)) {
                    btnFollowUser.setVisibility(View.GONE);
                    return;
                }

                btnFollowUser.setVisibility(View.VISIBLE);

                if (isFollowing) {
                    btnFollowUser.setText("Following");
                    btnFollowUser.setTextColor(Color.WHITE);
                    btnFollowUser.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
                    btnFollowUser.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#80FFFFFF")));
                } else if (isPending) {
                    btnFollowUser.setText("Requested");
                    btnFollowUser.setTextColor(Color.parseColor("#CCCCCC"));
                    btnFollowUser.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
                    btnFollowUser.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#40FFFFFF")));
                } else {
                    btnFollowUser.setText("Follow");
                    btnFollowUser.setTextColor(Color.WHITE);
                    btnFollowUser.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#26FFFFFF")));
                    btnFollowUser.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#66FFFFFF")));
                }
            }

            private void setupRealtimeListeners(String postId) {
                if (TextUtils.isEmpty(postId)) return;

                String currentUid = UserRepository.getInstance().getCurrentUid();
                FirebaseFirestore db = FirebaseFirestore.getInstance();

                // Likes Listener
                likesRegistration = db.collection("snaps").document(postId)
                        .collection("likes")
                        .addSnapshotListener((snapshot, e) -> {
                            if (e != null || snapshot == null) return;

                            likeCount = snapshot.size();
                            if (tvLikeCount != null) {
                                tvLikeCount.setText(formatCount(likeCount));
                            }

                            if (currentUid != null) {
                                boolean likedByMe = false;
                                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                    if (currentUid.equals(doc.getId())) {
                                        likedByMe = true;
                                        break;
                                    }
                                }
                                isLiked = likedByMe;
                                updateLikeUI();
                            }
                        });

                // Comments Listener Count
                commentsRegistration = db.collection("snaps").document(postId)
                        .collection("comments")
                        .addSnapshotListener((snapshot, e) -> {
                            if (e != null || snapshot == null) return;
                            commentCount = snapshot.size();
                            if (tvCommentCount != null) {
                                tvCommentCount.setText(formatCount(commentCount));
                            }
                        });
            }

            private void toggleLikeState() {
                if (currentItem == null || TextUtils.isEmpty(currentItem.id)) return;
                String postId = currentItem.id;
                String currentUid = UserRepository.getInstance().getCurrentUid();
                if (currentUid == null) {
                    Toast.makeText(itemView.getContext(), "Please sign in to like posts", Toast.LENGTH_SHORT).show();
                    return;
                }

                isLiked = !isLiked;
                if (isLiked) {
                    likeCount++;
                } else {
                    likeCount = Math.max(0, likeCount - 1);
                }

                updateLikeUI();
                if (tvLikeCount != null) {
                    tvLikeCount.setText(formatCount(likeCount));
                }

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                DocumentReference postRef = db.collection("snaps").document(postId);
                DocumentReference likeRef = postRef.collection("likes").document(currentUid);

                if (isLiked) {
                    Map<String, Object> likeDoc = new HashMap<>();
                    likeDoc.put("uid", currentUid);
                    likeDoc.put("createdAt", FieldValue.serverTimestamp());

                    likeRef.set(likeDoc)
                            .addOnSuccessListener(aVoid -> postRef.update("likesCount", FieldValue.increment(1)))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to set like", e));
                } else {
                    likeRef.delete()
                            .addOnSuccessListener(aVoid -> postRef.update("likesCount", FieldValue.increment(-1)))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to remove like", e));
                }
            }

            private void updateLikeUI() {
                if (ivActionLike != null) {
                    if (isLiked) {
                        ivActionLike.setImageResource(R.drawable.ic_heart_filled);
                    } else {
                        ivActionLike.setImageResource(R.drawable.ic_heart);
                    }
                }
            }

            private void toggleBookmarkState() {
                isBookmarked = !isBookmarked;
                if (ivActionBookmark != null) {
                    if (isBookmarked) {
                        ivActionBookmark.setColorFilter(Color.parseColor("#3897F0"));
                        Toast.makeText(itemView.getContext(), "Saved to collection", Toast.LENGTH_SHORT).show();
                    } else {
                        ivActionBookmark.clearColorFilter();
                    }
                }
            }

            private void openCommentBottomSheet() {
                if (currentItem == null || TextUtils.isEmpty(currentItem.id)) return;
                CommentBottomSheetDialog dialog = CommentBottomSheetDialog.newInstance(currentItem.id);
                dialog.show(getParentFragmentManager(), "CommentBottomSheetDialog");
            }

            private void sharePostUrl() {
                if (currentItem == null || TextUtils.isEmpty(currentItem.id)) return;
                SharePostToFriendsBottomSheet dialog = SharePostToFriendsBottomSheet.newInstance(currentItem.id, currentItem.mediaUrl, currentItem.title, currentItem.caption);
                dialog.show(getParentFragmentManager(), "SharePostToFriendsBottomSheet");
            }

            private void openUserProfile() {
                if (currentItem == null || TextUtils.isEmpty(currentItem.senderUid)) return;
                UserProfileDetailsDialog dialog = UserProfileDetailsDialog.newInstance(currentItem.senderUid);
                dialog.show(getParentFragmentManager(), "UserProfileDetailsDialog");
            }

            private String formatCount(int count) {
                if (count < 1000) return String.valueOf(count);
                if (count < 1000000) return String.format(Locale.US, "%.1fk", count / 1000.0f);
                return String.format(Locale.US, "%.1fM", count / 1000000.0f);
            }
        }
    }
}
