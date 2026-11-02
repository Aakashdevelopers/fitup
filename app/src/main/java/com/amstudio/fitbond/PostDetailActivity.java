package com.amstudio.fitbond;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.FriendRepository;
import com.amstudio.fitbond.repository.UserRepository;
import com.amstudio.fitbond.util.AvatarLoader;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class PostDetailActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "extra_post_id";
    public static final String EXTRA_MEDIA_URL = "extra_media_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CAPTION = "extra_caption";
    public static final String EXTRA_SENDER_UID = "extra_sender_uid";

    // Views
    private ImageButton btnBack;
    private ImageButton btnActionAdd;
    private ImageView ivPostMedia;

    // Right Action Column Views
    private View actionLikeContainer;
    private ImageView ivActionLike;
    private TextView tvLikeCount;

    private View actionCommentContainer;
    private TextView tvCommentCount;

    private ImageView ivActionShare;
    private ImageView ivActionBookmark;

    // Bottom Profile Overlay Views
    private View layoutCreatorInfo;
    private ShapeableImageView ivCreatorAvatar;
    private TextView tvCreatorUsername;
    private MaterialButton btnFollowUser;
    private TextView tvPostCaption;

    // State
    private String postId;
    private String mediaUrl;
    private String title;
    private String caption;
    private String senderUid;

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

    public static void open(Context context, String postId, String mediaUrl, String title, String caption, String senderUid) {
        Intent intent = new Intent(context, PostDetailActivity.class);
        intent.putExtra(EXTRA_POST_ID, postId);
        intent.putExtra(EXTRA_MEDIA_URL, mediaUrl);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_CAPTION, caption);
        intent.putExtra(EXTRA_SENDER_UID, senderUid);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_post_detail);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        View rootLayout = findViewById(R.id.root_post_detail);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        parseIntentData();
        initViews();
        setupClickListeners();

        loadPostMedia();
        loadCreatorProfile();
        setupRealtimeListeners();
        checkFollowStatus();
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            postId = intent.getStringExtra(EXTRA_POST_ID);
            mediaUrl = intent.getStringExtra(EXTRA_MEDIA_URL);
            title = intent.getStringExtra(EXTRA_TITLE);
            caption = intent.getStringExtra(EXTRA_CAPTION);
            senderUid = intent.getStringExtra(EXTRA_SENDER_UID);
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back_post_detail);
        btnActionAdd = findViewById(R.id.btn_action_add);
        ivPostMedia = findViewById(R.id.iv_post_media);

        actionLikeContainer = findViewById(R.id.action_like_container);
        ivActionLike = findViewById(R.id.iv_action_like);
        tvLikeCount = findViewById(R.id.tv_like_count);

        actionCommentContainer = findViewById(R.id.action_comment_container);
        tvCommentCount = findViewById(R.id.tv_comment_count);

        ivActionShare = findViewById(R.id.iv_action_share);
        ivActionBookmark = findViewById(R.id.iv_action_bookmark);

        layoutCreatorInfo = findViewById(R.id.layout_creator_info);
        ivCreatorAvatar = findViewById(R.id.iv_creator_avatar);
        tvCreatorUsername = findViewById(R.id.tv_creator_username);
        btnFollowUser = findViewById(R.id.btn_follow_user);
        tvPostCaption = findViewById(R.id.tv_post_caption);
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnActionAdd != null) {
            btnActionAdd.setOnClickListener(v -> {
                Intent intent = new Intent(this, NewPostActivity.class);
                startActivity(intent);
            });
        }

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

        if (layoutCreatorInfo != null) {
            layoutCreatorInfo.setOnClickListener(v -> openUserProfile());
        }

        if (ivCreatorAvatar != null) {
            ivCreatorAvatar.setOnClickListener(v -> openUserProfile());
        }

        if (tvCreatorUsername != null) {
            tvCreatorUsername.setOnClickListener(v -> openUserProfile());
        }
    }

    private void openUserProfile() {
        if (!TextUtils.isEmpty(senderUid)) {
            UserProfileDetailsDialog dialog = UserProfileDetailsDialog.newInstance(senderUid);
            dialog.show(getSupportFragmentManager(), "UserProfileDetailsDialog");
        }
    }

    private void loadPostMedia() {
        if (ivPostMedia != null && !TextUtils.isEmpty(mediaUrl)) {
            Glide.with(this)
                    .load(mediaUrl)
                    .placeholder(R.drawable.bg_sample_gym_preview)
                    .error(R.drawable.bg_sample_gym_preview)
                    .into(ivPostMedia);
        }

        updateCaptionUI(title, caption);
    }

    private void updateCaptionUI(String titleText, String captionText) {
        if (tvPostCaption == null) return;

        boolean hasTitle = !TextUtils.isEmpty(titleText);
        boolean hasCaption = !TextUtils.isEmpty(captionText);

        if (hasTitle && hasCaption && !titleText.trim().equalsIgnoreCase(captionText.trim())) {
            android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
            ssb.append(titleText.trim(), new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append("\n");
            ssb.append(captionText.trim());
            tvPostCaption.setText(ssb);
            tvPostCaption.setVisibility(View.VISIBLE);
        } else if (hasTitle) {
            android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
            ssb.append(titleText.trim(), new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvPostCaption.setText(ssb);
            tvPostCaption.setVisibility(View.VISIBLE);
        } else if (hasCaption) {
            tvPostCaption.setText(captionText.trim());
            tvPostCaption.setVisibility(View.VISIBLE);
        } else {
            tvPostCaption.setVisibility(View.GONE);
        }
    }

    private void loadCreatorProfile() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (!TextUtils.isEmpty(senderUid) && senderUid.equals(currentUid)) {
            if (btnFollowUser != null) {
                btnFollowUser.setVisibility(View.GONE);
            }
        } else if (btnFollowUser != null) {
            btnFollowUser.setVisibility(View.VISIBLE);
        }

        if (!TextUtils.isEmpty(senderUid)) {
            UserRepository.getInstance().getUserProfile(senderUid, new UserRepository.ProfileCallback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    if (isFinishing() || isDestroyed() || profile == null) return;
                    if (tvCreatorUsername != null) {
                        String name = !TextUtils.isEmpty(profile.getUsername()) ?
                                profile.getUsername() : profile.getDisplayName();
                        tvCreatorUsername.setText(name != null ? name : "user");
                    }
                    if (ivCreatorAvatar != null) {
                        AvatarLoader.loadAvatar(PostDetailActivity.this, profile.getAvatarUrl(), ivCreatorAvatar);
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (tvCreatorUsername != null) tvCreatorUsername.setText("user");
                }
            });
        }
    }

    private void checkFollowStatus() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null || TextUtils.isEmpty(senderUid)) return;

        if (senderUid.equals(currentUid)) {
            if (btnFollowUser != null) {
                btnFollowUser.setVisibility(View.GONE);
            }
            return;
        }

        if (btnFollowUser != null) {
            btnFollowUser.setVisibility(View.VISIBLE);
        }

        FriendRepository.getInstance().getAcceptedFriends(new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(java.util.List<UserProfile> friends) {
                if (isFinishing() || isDestroyed()) return;
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
                if (isFinishing() || isDestroyed()) return;
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
                    if (isFinishing() || isDestroyed()) return;
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
                    if (isFinishing() || isDestroyed()) return;
                    isFollowing = false;
                    isPending = false;
                    updateFollowButtonUI();
                });
    }

    private void toggleFollowStatus() {
        if (TextUtils.isEmpty(senderUid) || isFollowLoading) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) {
            Toast.makeText(this, "Please sign in to follow users", Toast.LENGTH_SHORT).show();
            return;
        }

        if (senderUid.equals(currentUid)) return;

        isFollowLoading = true;
        if (btnFollowUser != null) {
            btnFollowUser.setEnabled(false);
        }

        if (isFollowing) {
            FriendRepository.getInstance().removeFriend(senderUid, new FriendRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    isFollowLoading = false;
                    if (isFinishing() || isDestroyed()) return;
                    isFollowing = false;
                    isPending = false;
                    if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                    updateFollowButtonUI();
                    Toast.makeText(PostDetailActivity.this, "Unfollowed user", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    isFollowLoading = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                    Toast.makeText(PostDetailActivity.this, "Failed to unfollow", Toast.LENGTH_SHORT).show();
                }
            });
        } else if (isPending) {
            isFollowLoading = false;
            if (btnFollowUser != null) btnFollowUser.setEnabled(true);
            Toast.makeText(PostDetailActivity.this, "Follow request pending", Toast.LENGTH_SHORT).show();
        } else {
            FriendRepository.getInstance().sendFriendRequest(senderUid, new FriendRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    isFollowLoading = false;
                    if (isFinishing() || isDestroyed()) return;
                    isPending = true;
                    if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                    updateFollowButtonUI();
                    Toast.makeText(PostDetailActivity.this, "Follow request sent!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    isFollowLoading = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (btnFollowUser != null) btnFollowUser.setEnabled(true);
                    Toast.makeText(PostDetailActivity.this, "Failed to send follow request", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateFollowButtonUI() {
        if (btnFollowUser == null) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (!TextUtils.isEmpty(senderUid) && senderUid.equals(currentUid)) {
            btnFollowUser.setVisibility(View.GONE);
            return;
        }

        btnFollowUser.setVisibility(View.VISIBLE);

        if (isFollowing) {
            btnFollowUser.setText("Following");
            btnFollowUser.setTextColor(Color.WHITE);
            btnFollowUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
            btnFollowUser.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#80FFFFFF")));
        } else if (isPending) {
            btnFollowUser.setText("Requested");
            btnFollowUser.setTextColor(Color.parseColor("#CCCCCC"));
            btnFollowUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
            btnFollowUser.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#40FFFFFF")));
        } else {
            btnFollowUser.setText("Follow");
            btnFollowUser.setTextColor(Color.WHITE);
            btnFollowUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#26FFFFFF")));
            btnFollowUser.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#66FFFFFF")));
        }
    }

    private void setupRealtimeListeners() {
        if (TextUtils.isEmpty(postId)) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Post Document Listener (title, caption, mediaUrl, creator)
        postDocRegistration = db.collection("snaps").document(postId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists() || isFinishing()) return;
                    String docTitle = doc.getString("title");
                    String docCaption = doc.getString("caption");
                    String docMediaUrl = doc.getString("mediaUrl");
                    String docSenderUid = doc.getString("senderUid");

                    if (!TextUtils.isEmpty(docTitle)) title = docTitle;
                    if (!TextUtils.isEmpty(docCaption)) caption = docCaption;
                    if (!TextUtils.isEmpty(docMediaUrl) && TextUtils.isEmpty(mediaUrl)) {
                        mediaUrl = docMediaUrl;
                        if (ivPostMedia != null) {
                            Glide.with(this).load(mediaUrl).placeholder(R.drawable.bg_sample_gym_preview).into(ivPostMedia);
                        }
                    }
                    if (!TextUtils.isEmpty(docSenderUid) && (TextUtils.isEmpty(senderUid) || !senderUid.equals(docSenderUid))) {
                        senderUid = docSenderUid;
                        loadCreatorProfile();
                        checkFollowStatus();
                    }

                    updateCaptionUI(title, caption);
                });

        // 2. Likes Listener
        likesRegistration = db.collection("snaps").document(postId)
                .collection("likes")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || isFinishing()) return;

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

        // 3. Comments Listener Count
        commentsRegistration = db.collection("snaps").document(postId)
                .collection("comments")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || isFinishing()) return;
                    commentCount = snapshot.size();
                    if (tvCommentCount != null) {
                        tvCommentCount.setText(formatCount(commentCount));
                    }
                });
    }

    private void toggleLikeState() {
        if (TextUtils.isEmpty(postId)) return;
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) {
            Toast.makeText(this, "Please sign in to like posts", Toast.LENGTH_SHORT).show();
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
        com.google.firebase.firestore.DocumentReference postRef = db.collection("snaps").document(postId);
        com.google.firebase.firestore.DocumentReference likeRef = postRef.collection("likes").document(currentUid);

        if (isLiked) {
            Map<String, Object> likeDoc = new HashMap<>();
            likeDoc.put("uid", currentUid);
            likeDoc.put("createdAt", FieldValue.serverTimestamp());

            likeRef.set(likeDoc)
                    .addOnSuccessListener(aVoid -> postRef.update("likesCount", FieldValue.increment(1)))
                    .addOnFailureListener(e -> Log.e("PostDetailActivity", "Failed to set like", e));
        } else {
            likeRef.delete()
                    .addOnSuccessListener(aVoid -> postRef.update("likesCount", FieldValue.increment(-1)))
                    .addOnFailureListener(e -> Log.e("PostDetailActivity", "Failed to remove like", e));
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
                Toast.makeText(this, "Saved to collection", Toast.LENGTH_SHORT).show();
            } else {
                ivActionBookmark.clearColorFilter();
            }
        }
    }

    private void openCommentBottomSheet() {
        if (TextUtils.isEmpty(postId)) return;
        CommentBottomSheetDialog dialog = CommentBottomSheetDialog.newInstance(postId);
        dialog.show(getSupportFragmentManager(), "CommentBottomSheetDialog");
    }

    private void sharePostUrl() {
        if (TextUtils.isEmpty(postId)) return;
        SharePostToFriendsBottomSheet dialog = SharePostToFriendsBottomSheet.newInstance(postId, mediaUrl, title, caption);
        dialog.show(getSupportFragmentManager(), "SharePostToFriendsBottomSheet");
    }

    private String formatCount(int count) {
        if (count >= 1000000) {
            return String.format(java.util.Locale.US, "%.1fM", count / 1000000.0);
        } else if (count >= 1000) {
            return String.format(java.util.Locale.US, "%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
}