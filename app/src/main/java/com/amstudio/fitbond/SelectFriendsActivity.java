package com.amstudio.fitbond;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitbond.model.SnapMetadata;
import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.FriendRepository;
import com.amstudio.fitbond.repository.SnapRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Destination Screen for Private Snap sending.
 * Allows selecting gym friends for private friend-to-friend delivery.
 */
public class SelectFriendsActivity extends AppCompatActivity {

    private View rootLayout;
    private View headerView;
    private View bottomSendBar;
    private ImageButton btnBack;
    private EditText etSearch;
    private RecyclerView rvFriends;
    private MaterialButton btnSendSnapNow;

    private SelectFriendsAdapter adapter;
    private final List<FriendModel> friendList = new ArrayList<>();
    private final Set<String> selectedFriendIds = new HashSet<>();
    private String mediaUriStr;
    private String mediaType = SnapPreviewActivity.MEDIA_TYPE_PHOTO;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_select_friends);

        parseIntentData(getIntent());

        // Window Insets & System Bar Icons
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        initializeViews();
        applyWindowInsets();
        setupRecyclerView();
        populateFriendsList();
        setupListeners();
        updateSendButtonState();
    }

    private void initializeViews() {
        rootLayout = findViewById(R.id.select_friends_root);
        headerView = findViewById(R.id.select_friends_header);
        bottomSendBar = findViewById(R.id.layout_bottom_send_bar);
        btnBack = findViewById(R.id.btn_back_friends);
        etSearch = findViewById(R.id.et_search_friends);
        rvFriends = findViewById(R.id.rv_select_friends);
        btnSendSnapNow = findViewById(R.id.btn_send_snap_now);
    }

    private void applyWindowInsets() {
        if (rootLayout == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (headerView != null) {
                headerView.setPadding(
                        headerView.getPaddingLeft(),
                        systemBars.top + 12,
                        headerView.getPaddingRight(),
                        headerView.getPaddingBottom()
                );
            }

            if (bottomSendBar != null) {
                bottomSendBar.setPadding(
                        bottomSendBar.getPaddingLeft(),
                        bottomSendBar.getPaddingTop(),
                        bottomSendBar.getPaddingRight(),
                        systemBars.bottom + 16
                );
            }

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void populateFriendsList() {
        FriendRepository.getInstance().getAcceptedFriends(new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(List<UserProfile> friends) {
                friendList.clear();
                if (friends != null && !friends.isEmpty()) {
                    int[] avatars = {
                            R.drawable.bg_avatar_gradient_1, R.drawable.bg_avatar_gradient_2,
                            R.drawable.bg_avatar_gradient_3, R.drawable.bg_avatar_gradient_4,
                            R.drawable.bg_avatar_gradient_5, R.drawable.bg_avatar_gradient_6
                    };
                    for (int i = 0; i < friends.size(); i++) {
                        UserProfile user = friends.get(i);
                        String name = user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                                ? user.getDisplayName()
                                : (user.getUsername() != null ? user.getUsername() : "Gym Buddy");
                        String handle = "@" + (user.getUsername() != null ? user.getUsername() : "user");
                        int bgRes = avatars[i % avatars.length];
                        friendList.add(new FriendModel(user.getUid(), name, handle, user.isOnline(), bgRes));
                    }
                } else {
                    // Fallback sample friends if no real friends added yet
                    friendList.add(new FriendModel("1", "Jordan Miller", "@jordan_lift", false, R.drawable.bg_avatar_gradient_1));
                    friendList.add(new FriendModel("2", "Sarah Chen", "@sarah_fit", false, R.drawable.bg_avatar_gradient_2));
                    friendList.add(new FriendModel("3", "Marcus Vance", "@marcus_power", false, R.drawable.bg_avatar_gradient_3));
                    friendList.add(new FriendModel("4", "Chris Paul", "@cp_gains", false, R.drawable.bg_avatar_gradient_4));
                }
                if (adapter != null) {
                    adapter.setItems(friendList);
                }
            }

            @Override
            public void onError(Exception e) {
                friendList.clear();
                // Fallback sample friends on error
                friendList.add(new FriendModel("1", "Jordan Miller", "@jordan_lift", false, R.drawable.bg_avatar_gradient_1));
                friendList.add(new FriendModel("2", "Sarah Chen", "@sarah_fit", false, R.drawable.bg_avatar_gradient_2));
                friendList.add(new FriendModel("3", "Marcus Vance", "@marcus_power", false, R.drawable.bg_avatar_gradient_3));
                if (adapter != null) {
                    adapter.setItems(friendList);
                }
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new SelectFriendsAdapter(friendList, (friend, isSelected) -> {
            if (isSelected) {
                selectedFriendIds.add(friend.id);
            } else {
                selectedFriendIds.remove(friend.id);
            }
            updateSendButtonState();
        });

        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(adapter);
    }

    private void parseIntentData(@Nullable Intent intent) {
        if (intent != null) {
            mediaUriStr = intent.getStringExtra(SnapPreviewActivity.EXTRA_MEDIA_URI);
            String typeExtra = intent.getStringExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE);
            if (typeExtra != null) {
                mediaType = typeExtra;
            }
        }
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

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

        if (btnSendSnapNow != null) {
            btnSendSnapNow.setOnClickListener(v -> {
                int count = selectedFriendIds.size();
                if (count == 0) {
                    Toast.makeText(this, "Please select at least one friend", Toast.LENGTH_SHORT).show();
                    return;
                }

                ArrayList<String> selectedNames = new ArrayList<>();
                for (FriendModel friend : friendList) {
                    if (selectedFriendIds.contains(friend.id)) {
                        selectedNames.add(friend.name);
                    }
                }

                if (mediaUriStr != null) {
                    btnSendSnapNow.setEnabled(false);
                    btnSendSnapNow.setText("Sending...");

                    SnapRepository.getInstance().sendPrivateSnap(
                            SelectFriendsActivity.this,
                            Uri.parse(mediaUriStr),
                            mediaType,
                            new ArrayList<>(selectedFriendIds),
                            15,
                            new SnapRepository.SnapUploadCallback() {
                                @Override
                                public void onSuccess(SnapMetadata metadata) {
                                    Intent intent = new Intent(SelectFriendsActivity.this, SnapSentConfirmationActivity.class);
                                    intent.putExtra(SnapSentConfirmationActivity.EXTRA_MEDIA_URI, mediaUriStr);
                                    intent.putExtra(SnapSentConfirmationActivity.EXTRA_MEDIA_TYPE, mediaType);
                                    intent.putStringArrayListExtra(SnapSentConfirmationActivity.EXTRA_RECIPIENT_NAMES, selectedNames);
                                    startActivity(intent);
                                    finish();
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.w("SelectFriendsActivity", "Snap upload warning: " + e.getLocalizedMessage());
                                    Intent intent = new Intent(SelectFriendsActivity.this, SnapSentConfirmationActivity.class);
                                    intent.putExtra(SnapSentConfirmationActivity.EXTRA_MEDIA_URI, mediaUriStr);
                                    intent.putExtra(SnapSentConfirmationActivity.EXTRA_MEDIA_TYPE, mediaType);
                                    intent.putStringArrayListExtra(SnapSentConfirmationActivity.EXTRA_RECIPIENT_NAMES, selectedNames);
                                    startActivity(intent);
                                    finish();
                                }
                            }
                    );
                } else {
                    Intent intent = new Intent(SelectFriendsActivity.this, SnapSentConfirmationActivity.class);
                    intent.putExtra(SnapSentConfirmationActivity.EXTRA_MEDIA_TYPE, mediaType);
                    intent.putStringArrayListExtra(SnapSentConfirmationActivity.EXTRA_RECIPIENT_NAMES, selectedNames);
                    startActivity(intent);
                    finish();
                }
            });
        }
    }

    private void updateSendButtonState() {
        int count = selectedFriendIds.size();
        if (btnSendSnapNow != null) {
            if (count > 0) {
                btnSendSnapNow.setText(String.format(Locale.US, "Send Snap (%d)", count));
                btnSendSnapNow.setEnabled(true);
                btnSendSnapNow.setAlpha(1.0f);
            } else {
                btnSendSnapNow.setText(R.string.btn_send_snap_now);
                btnSendSnapNow.setEnabled(false);
                btnSendSnapNow.setAlpha(0.5f);
            }
        }
    }

    // Model Class
    public static class FriendModel {
        String id;
        String name;
        String username;
        boolean isOnline;
        int avatarBgRes;

        public FriendModel(String id, String name, String username, boolean isOnline, int avatarBgRes) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.isOnline = isOnline;
            this.avatarBgRes = avatarBgRes;
        }
    }

    // RecyclerView Adapter
    private class SelectFriendsAdapter extends RecyclerView.Adapter<SelectFriendsAdapter.FriendViewHolder> {

        private final List<FriendModel> originalList = new ArrayList<>();
        private final List<FriendModel> filteredList = new ArrayList<>();
        private final OnFriendSelectListener listener;

        public SelectFriendsAdapter(List<FriendModel> friendList, OnFriendSelectListener listener) {
            this.listener = listener;
            setItems(friendList);
        }

        public void setItems(List<FriendModel> items) {
            this.originalList.clear();
            if (items != null) {
                this.originalList.addAll(items);
            }
            filter(etSearch != null ? etSearch.getText().toString() : "");
        }

        public void filter(String query) {
            filteredList.clear();
            if (query == null || query.trim().isEmpty()) {
                filteredList.addAll(originalList);
            } else {
                String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
                for (FriendModel model : originalList) {
                    if ((model.name != null && model.name.toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                        (model.username != null && model.username.toLowerCase(Locale.ROOT).contains(lowerQuery))) {
                        filteredList.add(model);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_select_friend, parent, false);
            return new FriendViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
            FriendModel model = filteredList.get(position);
            holder.tvName.setText(model.name);
            holder.tvUsername.setText(model.username);
            holder.ivAvatar.setBackgroundResource(model.avatarBgRes);
            holder.vOnlineBadge.setVisibility(model.isOnline ? View.VISIBLE : View.GONE);

            boolean isChecked = selectedFriendIds.contains(model.id);
            holder.cbSelect.setChecked(isChecked);

            holder.itemView.setOnClickListener(v -> {
                boolean newChecked = !selectedFriendIds.contains(model.id);
                holder.cbSelect.setChecked(newChecked);
                if (listener != null) {
                    listener.onSelectChanged(model, newChecked);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class FriendViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            View vOnlineBadge;
            TextView tvName;
            TextView tvUsername;
            CheckBox cbSelect;

            public FriendViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_friend_avatar);
                vOnlineBadge = itemView.findViewById(R.id.v_online_badge);
                tvName = itemView.findViewById(R.id.tv_friend_name);
                tvUsername = itemView.findViewById(R.id.tv_friend_username);
                cbSelect = itemView.findViewById(R.id.cb_select_friend);
            }
        }
    }

    private interface OnFriendSelectListener {
        void onSelectChanged(FriendModel friend, boolean isSelected);
    }
}
