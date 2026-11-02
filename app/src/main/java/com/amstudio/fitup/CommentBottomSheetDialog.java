package com.amstudio.fitup;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.UserRepository;
import com.amstudio.fitup.util.AvatarLoader;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommentBottomSheetDialog extends BottomSheetDialogFragment {

    private static final String ARG_POST_ID = "arg_post_id";

    private String postId;
    private RecyclerView rvComments;
    private TextView tvEmptyComments;
    private EditText etCommentInput;
    private ImageButton btnSendComment;

    private final List<CommentItem> commentList = new ArrayList<>();
    private CommentAdapter adapter;
    private ListenerRegistration commentsRegistration;

    public static CommentBottomSheetDialog newInstance(String postId) {
        CommentBottomSheetDialog fragment = new CommentBottomSheetDialog();
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            postId = getArguments().getString(ARG_POST_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_comment_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvComments = view.findViewById(R.id.rv_comments_list);
        tvEmptyComments = view.findViewById(R.id.tv_empty_comments);
        etCommentInput = view.findViewById(R.id.et_comment_input);
        btnSendComment = view.findViewById(R.id.btn_send_comment);

        adapter = new CommentAdapter(commentList);
        rvComments.setLayoutManager(new LinearLayoutManager(getContext()));
        rvComments.setAdapter(adapter);

        btnSendComment.setOnClickListener(v -> postComment());

        setupCommentsListener();
    }

    private void setupCommentsListener() {
        if (TextUtils.isEmpty(postId)) return;

        commentsRegistration = FirebaseFirestore.getInstance()
                .collection("snaps").document(postId)
                .collection("comments")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        android.util.Log.e("CommentBottomSheet", "Error fetching comments", e);
                        return;
                    }
                    if (snapshot == null || !isAdded()) return;

                    commentList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String id = doc.getId();
                        String uid = doc.getString("userUid");
                        String username = doc.getString("username");
                        String text = doc.getString("text");
                        String avatarUrl = doc.getString("avatarUrl");
                        Timestamp ts = doc.getTimestamp("createdAt");
                        long timeMs = ts != null ? ts.toDate().getTime() : System.currentTimeMillis();

                        if (!TextUtils.isEmpty(text)) {
                            commentList.add(new CommentItem(id, uid, username != null ? username : "User", text, avatarUrl, timeMs));
                        }
                    }

                    // Sort comments by timestamp ascending
                    commentList.sort((a, b) -> Long.compare(a.timestampMs, b.timestampMs));

                    adapter.notifyDataSetChanged();
                    boolean hasComments = !commentList.isEmpty();
                    rvComments.setVisibility(hasComments ? View.VISIBLE : View.GONE);
                    tvEmptyComments.setVisibility(hasComments ? View.GONE : View.VISIBLE);

                    if (hasComments) {
                        rvComments.post(() -> rvComments.smoothScrollToPosition(commentList.size() - 1));
                    }
                });
    }

    private void postComment() {
        String text = etCommentInput.getText() != null ? etCommentInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(postId)) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) {
            Toast.makeText(getContext(), "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        etCommentInput.setText("");

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        UserRepository.getInstance().getUserProfile(currentUid, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                String commentId = UUID.randomUUID().toString();
                String username = profile != null && !TextUtils.isEmpty(profile.getUsername()) ?
                        profile.getUsername() : (profile != null ? profile.getDisplayName() : "User");
                String avatarUrl = profile != null ? profile.getAvatarUrl() : null;

                Map<String, Object> commentMap = new HashMap<>();
                commentMap.put("id", commentId);
                commentMap.put("userUid", currentUid);
                commentMap.put("username", username);
                commentMap.put("text", text);
                commentMap.put("avatarUrl", avatarUrl);
                commentMap.put("createdAt", FieldValue.serverTimestamp());

                db.collection("snaps").document(postId)
                        .collection("comments").document(commentId)
                        .set(commentMap)
                        .addOnSuccessListener(aVoid -> db.collection("snaps").document(postId).update("commentsCount", FieldValue.increment(1)));
            }

            @Override
            public void onError(Exception e) {
                String commentId = UUID.randomUUID().toString();
                Map<String, Object> commentMap = new HashMap<>();
                commentMap.put("id", commentId);
                commentMap.put("userUid", currentUid);
                commentMap.put("username", "User");
                commentMap.put("text", text);
                commentMap.put("createdAt", FieldValue.serverTimestamp());

                db.collection("snaps").document(postId)
                        .collection("comments").document(commentId)
                        .set(commentMap)
                        .addOnSuccessListener(aVoid -> db.collection("snaps").document(postId).update("commentsCount", FieldValue.increment(1)));
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (commentsRegistration != null) {
            commentsRegistration.remove();
            commentsRegistration = null;
        }
    }

    public static class CommentItem {
        public final String id;
        public final String userUid;
        public final String username;
        public final String text;
        public final String avatarUrl;
        public final long timestampMs;

        public CommentItem(String id, String userUid, String username, String text, String avatarUrl, long timestampMs) {
            this.id = id;
            this.userUid = userUid;
            this.username = username;
            this.text = text;
            this.avatarUrl = avatarUrl;
            this.timestampMs = timestampMs;
        }
    }

    private static class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

        private final List<CommentItem> list;

        CommentAdapter(List<CommentItem> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
            return new CommentViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
            holder.bind(list.get(position));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class CommentViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView ivAvatar;
            TextView tvUsername;
            TextView tvTime;
            TextView tvText;

            CommentViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_comment_avatar);
                tvUsername = itemView.findViewById(R.id.tv_comment_user);
                tvTime = itemView.findViewById(R.id.tv_comment_time);
                tvText = itemView.findViewById(R.id.tv_comment_text);
            }

            void bind(CommentItem item) {
                if (tvUsername != null) tvUsername.setText(item.username);
                if (tvText != null) tvText.setText(item.text);
                if (tvTime != null) {
                    long diffSec = (System.currentTimeMillis() - item.timestampMs) / 1000;
                    if (diffSec < 60) tvTime.setText("just now");
                    else if (diffSec < 3600) tvTime.setText((diffSec / 60) + "m");
                    else if (diffSec < 86400) tvTime.setText((diffSec / 3600) + "h");
                    else tvTime.setText((diffSec / 86400) + "d");
                }
                if (ivAvatar != null) {
                    AvatarLoader.loadAvatar(itemView.getContext(), item.avatarUrl, ivAvatar);
                }
            }
        }
    }
}