package com.amstudio.fitbond;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.FriendRepository;
import com.amstudio.fitbond.repository.UserRepository;
import com.amstudio.fitbond.util.AvatarLoader;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserProfileDetailsDialog extends DialogFragment {

    private static final String ARG_UID = "arg_uid";

    private ImageView ivAvatar;
    private TextView tvDisplayName;
    private TextView tvUsername;
    private TextView tvBio;
    private TextView tvLocation;
    private View layoutLocation;
    private MaterialButton btnFollow;
    private MaterialButton btnChat;
    private ImageButton btnClose;

    private String targetUid;
    private UserProfile loadedProfile;

    private boolean isFollowing = false;
    private boolean isPending = false;
    private boolean isFollowLoading = false;

    public static UserProfileDetailsDialog newInstance(@NonNull String userUid) {
        UserProfileDetailsDialog dialog = new UserProfileDetailsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_UID, userUid);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Dialog);
        if (getArguments() != null) {
            targetUid = getArguments().getString(ARG_UID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_user_profile_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        initViews(view);
        loadUserProfile();
        checkFollowStatus();
    }

    private void initViews(View view) {
        ivAvatar = view.findViewById(R.id.iv_dialog_avatar);
        tvDisplayName = view.findViewById(R.id.tv_dialog_display_name);
        tvUsername = view.findViewById(R.id.tv_dialog_username);
        tvBio = view.findViewById(R.id.tv_dialog_bio);
        tvLocation = view.findViewById(R.id.tv_dialog_location);
        layoutLocation = view.findViewById(R.id.layout_dialog_location);
        btnFollow = view.findViewById(R.id.btn_dialog_follow);
        btnChat = view.findViewById(R.id.btn_dialog_chat);
        btnClose = view.findViewById(R.id.btn_close_user_details);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        if (btnChat != null) {
            btnChat.setOnClickListener(v -> openIndividualChat());
        }

        if (btnFollow != null) {
            btnFollow.setOnClickListener(v -> toggleFollowStatus());
        }
    }

    private void loadUserProfile() {
        if (targetUid == null || targetUid.isEmpty()) {
            Toast.makeText(getContext(), "Invalid user ID", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        UserRepository.getInstance().getUserProfile(targetUid, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (isAdded() && profile != null) {
                    loadedProfile = profile;
                    populateData(profile);
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Could not load user details", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkFollowStatus() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null || TextUtils.isEmpty(targetUid)) return;

        if (targetUid.equals(currentUid)) {
            if (btnFollow != null) {
                btnFollow.setVisibility(View.GONE);
            }
            return;
        }

        if (btnFollow != null) {
            btnFollow.setVisibility(View.VISIBLE);
        }

        FriendRepository.getInstance().getAcceptedFriends(new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(java.util.List<UserProfile> friends) {
                if (!isAdded()) return;
                boolean found = false;
                if (friends != null) {
                    for (UserProfile friend : friends) {
                        if (friend != null && targetUid.equals(friend.getUid())) {
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
                    checkPendingRequestStatus(currentUid, targetUid);
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                checkPendingRequestStatus(currentUid, targetUid);
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
                    if (!isAdded()) return;
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
                    if (!isAdded()) return;
                    isFollowing = false;
                    isPending = false;
                    updateFollowButtonUI();
                });
    }

    private void toggleFollowStatus() {
        if (TextUtils.isEmpty(targetUid) || isFollowLoading) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) {
            Toast.makeText(getContext(), "Please sign in to follow users", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetUid.equals(currentUid)) return;

        isFollowLoading = true;
        if (btnFollow != null) {
            btnFollow.setEnabled(false);
        }

        if (isFollowing) {
            FriendRepository.getInstance().removeFriend(targetUid, new FriendRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    isFollowLoading = false;
                    if (!isAdded()) return;
                    isFollowing = false;
                    isPending = false;
                    if (btnFollow != null) btnFollow.setEnabled(true);
                    updateFollowButtonUI();
                    Toast.makeText(getContext(), "Unfollowed user", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    isFollowLoading = false;
                    if (!isAdded()) return;
                    if (btnFollow != null) btnFollow.setEnabled(true);
                    Toast.makeText(getContext(), "Failed to unfollow", Toast.LENGTH_SHORT).show();
                }
            });
        } else if (isPending) {
            isFollowLoading = false;
            if (btnFollow != null) btnFollow.setEnabled(true);
            Toast.makeText(getContext(), "Follow request pending", Toast.LENGTH_SHORT).show();
        } else {
            FriendRepository.getInstance().sendFriendRequest(targetUid, new FriendRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    isFollowLoading = false;
                    if (!isAdded()) return;
                    isPending = true;
                    if (btnFollow != null) btnFollow.setEnabled(true);
                    updateFollowButtonUI();
                    Toast.makeText(getContext(), "Follow request sent!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    isFollowLoading = false;
                    if (!isAdded()) return;
                    if (btnFollow != null) btnFollow.setEnabled(true);
                    Toast.makeText(getContext(), "Failed to send follow request", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateFollowButtonUI() {
        if (btnFollow == null || getContext() == null) return;

        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (!TextUtils.isEmpty(targetUid) && targetUid.equals(currentUid)) {
            btnFollow.setVisibility(View.GONE);
            return;
        }

        btnFollow.setVisibility(View.VISIBLE);

        if (isFollowing) {
            btnFollow.setText("Following");
            btnFollow.setTextColor(Color.WHITE);
            btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
        } else if (isPending) {
            btnFollow.setText("Requested");
            btnFollow.setTextColor(Color.parseColor("#CCCCCC"));
            btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
        } else {
            btnFollow.setText("Follow");
            btnFollow.setTextColor(Color.WHITE);
            btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#26FFFFFF")));
        }
    }

    private void populateData(@NonNull UserProfile profile) {
        if (tvDisplayName != null) {
            tvDisplayName.setText(!TextUtils.isEmpty(profile.getDisplayName()) ? profile.getDisplayName() : "FitUp Member");
        }

        if (tvUsername != null) {
            tvUsername.setText(!TextUtils.isEmpty(profile.getUsername()) ? "@" + profile.getUsername() : "");
        }

        if (tvBio != null) {
            tvBio.setText(!TextUtils.isEmpty(profile.getBio()) ? profile.getBio() : "Gym enthusiast 💪");
        }

        if (tvLocation != null && layoutLocation != null) {
            if (!TextUtils.isEmpty(profile.getLocation())) {
                tvLocation.setText(profile.getLocation());
                layoutLocation.setVisibility(View.VISIBLE);
            } else {
                layoutLocation.setVisibility(View.GONE);
            }
        }

        if (ivAvatar != null) {
            AvatarLoader.loadAvatar(this, profile.getAvatarUrl(), ivAvatar);
        }
    }

    private void openIndividualChat() {
        if (getContext() == null || loadedProfile == null) return;

        Intent intent = new Intent(getContext(), IndividualChatActivity.class);
        intent.putExtra("CHAT_ID", loadedProfile.getUid() != null ? loadedProfile.getUid() : targetUid);
        intent.putExtra("CHAT_NAME", loadedProfile.getDisplayName() != null ? loadedProfile.getDisplayName() : "User");
        intent.putExtra("CHAT_USER_ID", loadedProfile.getUid() != null ? loadedProfile.getUid() : targetUid);
        startActivity(intent);
        dismiss();
    }
}
