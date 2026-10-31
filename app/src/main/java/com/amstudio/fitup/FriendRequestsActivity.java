package com.amstudio.fitup;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.FriendRequest;
import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.ActiveCallManager;
import com.amstudio.fitup.repository.FriendRepository;
import com.amstudio.fitup.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FriendRequestsActivity extends AppCompatActivity {

    public static final String EXTRA_FOCUS_SEARCH = "extra_focus_search";

    private ImageButton btnBack;
    private ImageButton btnClose;
    private EditText etSearchUsers;
    private RecyclerView rvRequests;
    private RecyclerView rvSearchResults;
    private TextView tvRequestsHeader;
    private TextView tvDiscoverHeader;

    private RequestsAdapter requestsAdapter;
    private SearchUsersAdapter searchAdapter;
    private final List<UserProfile> discoverableUsers = new ArrayList<>();
    private boolean hasPendingRequests = false;

    // Active Call Banner Views
    private View activeCallBanner;
    private TextView tvBannerTitle;
    private TextView tvBannerSub;
    private TextView tvBannerAvatarInitials;
    private ImageView ivCallIcon;
    private View btnSpeaker;
    private View btnCamera;
    private View btnMute;
    private View btnEndCall;
    private View btnDropdown;
    private View layoutActions;

    private ActiveCallManager.OnCallStateChangeListener activeCallStateListener;
    private Handler bannerTimerHandler;
    private Runnable bannerTimerRunnable;

    public static void start(Context context, boolean focusSearch) {
        Intent intent = new Intent(context, FriendRequestsActivity.class);
        intent.putExtra(EXTRA_FOCUS_SEARCH, focusSearch);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_requests);

        View rootLayout = findViewById(R.id.layout_requests_root);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                int statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                int navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(v.getPaddingLeft(), statusBarTop, v.getPaddingRight(), navBarBottom);
                return insets;
            });
        }

        btnBack = findViewById(R.id.btn_requests_back);
        btnClose = findViewById(R.id.btn_requests_close);
        etSearchUsers = findViewById(R.id.et_search_users);
        rvRequests = findViewById(R.id.rv_pending_requests);
        rvSearchResults = findViewById(R.id.rv_user_search_results);
        tvRequestsHeader = findViewById(R.id.tv_requests_header);
        tvDiscoverHeader = findViewById(R.id.tv_discover_header);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        setupActiveCallBanner();
        setupRecyclerViews();
        loadPendingRequests();
        loadDiscoverableUsers();
        setupSearch();

        boolean focusSearch = getIntent().getBooleanExtra(EXTRA_FOCUS_SEARCH, false);
        if (focusSearch && etSearchUsers != null) {
            etSearchUsers.requestFocus();
            etSearchUsers.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearchUsers, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 200);
        }
    }

    private void setupActiveCallBanner() {
        activeCallBanner = findViewById(R.id.layout_active_call_banner);
        if (activeCallBanner == null) return;

        tvBannerTitle = activeCallBanner.findViewById(R.id.tv_banner_call_title);
        tvBannerSub = activeCallBanner.findViewById(R.id.tv_banner_call_sub);
        tvBannerAvatarInitials = activeCallBanner.findViewById(R.id.tv_banner_avatar_initials);
        ivCallIcon = activeCallBanner.findViewById(R.id.iv_banner_call_icon);

        btnSpeaker = activeCallBanner.findViewById(R.id.btn_banner_speaker);
        btnCamera = activeCallBanner.findViewById(R.id.btn_banner_camera);
        btnMute = activeCallBanner.findViewById(R.id.btn_banner_mute);
        btnEndCall = activeCallBanner.findViewById(R.id.btn_banner_end_call);
        btnDropdown = activeCallBanner.findViewById(R.id.btn_banner_dropdown);
        layoutActions = activeCallBanner.findViewById(R.id.layout_banner_actions);

        if (btnDropdown != null && layoutActions != null) {
            btnDropdown.setOnClickListener(v -> {
                boolean isCurrentlyVisible = layoutActions.getVisibility() == View.VISIBLE;
                if (isCurrentlyVisible) {
                    layoutActions.setVisibility(View.GONE);
                    if (btnDropdown instanceof ImageButton) {
                        ((ImageButton) btnDropdown).setImageResource(R.drawable.ic_chevron_down);
                    }
                } else {
                    layoutActions.setVisibility(View.VISIBLE);
                    if (btnDropdown instanceof ImageButton) {
                        ((ImageButton) btnDropdown).setImageResource(R.drawable.ic_chevron_up);
                    }
                }
            });
        }

        if (btnSpeaker != null) {
            btnSpeaker.setOnClickListener(v -> ActiveCallManager.getInstance().toggleSpeaker());
        }
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> ActiveCallManager.getInstance().toggleCamera());
        }
        if (btnMute != null) {
            btnMute.setOnClickListener(v -> ActiveCallManager.getInstance().toggleMute());
        }
        if (btnEndCall != null) {
            btnEndCall.setOnClickListener(v -> ActiveCallManager.getInstance().endCall());
        }

        View layoutText = activeCallBanner.findViewById(R.id.layout_banner_text);
        View layoutAvatar = activeCallBanner.findViewById(R.id.layout_banner_avatar);

        View.OnClickListener expandListener = v -> {
            String cId = ActiveCallManager.getInstance().getActiveCallId();
            if (cId != null) {
                String targetUid = ActiveCallManager.getInstance().getTargetUid();
                String name = ActiveCallManager.getInstance().getUserName();
                String initials = ActiveCallManager.getInstance().getUserInitials();
                boolean isVideo = ActiveCallManager.getInstance().isVideo();
                TRTCCallActivity.start(this, cId, targetUid != null ? targetUid : "", name, initials, isVideo, false);
            }
        };

        if (layoutText != null) layoutText.setOnClickListener(expandListener);
        if (layoutAvatar != null) layoutAvatar.setOnClickListener(expandListener);
        activeCallBanner.setOnClickListener(null);

        activeCallStateListener = (isActive, callId, name, initials, isVideo, startTime) -> runOnUiThread(() -> {
            if (isActive && callId != null) {
                activeCallBanner.setVisibility(View.VISIBLE);

                if (tvBannerTitle != null) {
                    tvBannerTitle.setText(name != null && !name.isEmpty() ? name : "Ongoing Call");
                }
                if (tvBannerAvatarInitials != null) {
                    tvBannerAvatarInitials.setText(initials != null ? initials : "GB");
                }
                if (ivCallIcon != null) {
                    ivCallIcon.setImageResource(isVideo ? R.drawable.ic_video_call : R.drawable.ic_phone_call);
                }

                boolean speakerOn = ActiveCallManager.getInstance().isSpeakerOn();
                boolean cameraOn = ActiveCallManager.getInstance().isCameraOn();
                boolean muteOn = ActiveCallManager.getInstance().isMuteOn();

                if (btnSpeaker != null) {
                    btnSpeaker.setBackgroundResource(speakerOn ? R.drawable.bg_call_btn_white : R.drawable.bg_call_btn_dark);
                    if (btnSpeaker instanceof ImageButton) {
                        ((ImageButton) btnSpeaker).setColorFilter(speakerOn ? Color.BLACK : Color.WHITE);
                        ((ImageButton) btnSpeaker).setImageResource(speakerOn ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
                    }
                }

                if (btnCamera != null) {
                    btnCamera.setBackgroundResource(cameraOn ? R.drawable.bg_call_btn_dark : R.drawable.bg_call_btn_dark_active);
                    if (btnCamera instanceof ImageButton) {
                        ((ImageButton) btnCamera).setImageResource(cameraOn ? R.drawable.ic_video_call : R.drawable.ic_video_off);
                    }
                }

                if (btnMute != null) {
                    btnMute.setBackgroundResource(muteOn ? R.drawable.bg_call_btn_dark_active : R.drawable.bg_call_btn_dark);
                    if (btnMute instanceof ImageButton) {
                        ((ImageButton) btnMute).setImageResource(muteOn ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
                    }
                }

                startBannerTimer(startTime, isVideo);
            } else {
                activeCallBanner.setVisibility(View.GONE);
                stopBannerTimer();
            }
        });
    }

    private void startBannerTimer(long startTime, boolean isVideo) {
        stopBannerTimer();
        bannerTimerHandler = new Handler(Looper.getMainLooper());
        bannerTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (tvBannerSub != null && ActiveCallManager.getInstance().isActive()) {
                    long elapsedSec = Math.max(0, (System.currentTimeMillis() - startTime) / 1000);
                    long mm = elapsedSec / 60;
                    long ss = elapsedSec % 60;
                    String durationStr = String.format(Locale.getDefault(), "%02d:%02d", mm, ss);
                    String prefix = isVideo ? "FitUp Video • " : "FitUp Voice • ";
                    tvBannerSub.setText(prefix + durationStr);
                    bannerTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        bannerTimerHandler.post(bannerTimerRunnable);
    }

    private void stopBannerTimer() {
        if (bannerTimerHandler != null && bannerTimerRunnable != null) {
            bannerTimerHandler.removeCallbacks(bannerTimerRunnable);
            bannerTimerHandler = null;
            bannerTimerRunnable = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (activeCallStateListener != null) {
            ActiveCallManager.getInstance().addListener(activeCallStateListener);
            boolean isActive = ActiveCallManager.getInstance().isActive();
            if (isActive) {
                activeCallStateListener.onCallStateChanged(
                        true,
                        ActiveCallManager.getInstance().getActiveCallId(),
                        ActiveCallManager.getInstance().getUserName(),
                        ActiveCallManager.getInstance().getUserInitials(),
                        ActiveCallManager.getInstance().isVideo(),
                        ActiveCallManager.getInstance().getCallStartTimeMs()
                );
            } else if (activeCallBanner != null) {
                activeCallBanner.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (activeCallStateListener != null) {
            ActiveCallManager.getInstance().removeListener(activeCallStateListener);
        }
        stopBannerTimer();
    }

    private void setupRecyclerViews() {
        if (rvRequests != null) {
            rvRequests.setLayoutManager(new LinearLayoutManager(this));
            requestsAdapter = new RequestsAdapter();
            rvRequests.setAdapter(requestsAdapter);
        }

        if (rvSearchResults != null) {
            rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
            searchAdapter = new SearchUsersAdapter();
            rvSearchResults.setAdapter(searchAdapter);
        }
    }

    private void loadPendingRequests() {
        FriendRepository.getInstance().getPendingFriendRequests(new FriendRepository.FriendRequestsCallback() {
            @Override
            public void onSuccess(List<FriendRequest> requests) {
                if (!isFinishing() && !isDestroyed() && requestsAdapter != null) {
                    requestsAdapter.setItems(requests);
                    hasPendingRequests = requests != null && !requests.isEmpty();

                    String query = etSearchUsers != null ? etSearchUsers.getText().toString().trim() : "";
                    if (query.length() < 2) {
                        if (tvRequestsHeader != null) {
                            tvRequestsHeader.setVisibility(hasPendingRequests ? View.VISIBLE : View.GONE);
                            if (hasPendingRequests) {
                                tvRequestsHeader.setText("Pending Friend Requests (" + requests.size() + ")");
                            }
                        }
                        if (rvRequests != null) {
                            rvRequests.setVisibility(hasPendingRequests ? View.VISIBLE : View.GONE);
                        }
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing() && !isDestroyed()) {
                    hasPendingRequests = false;
                    if (tvRequestsHeader != null) tvRequestsHeader.setVisibility(View.GONE);
                    if (rvRequests != null) rvRequests.setVisibility(View.GONE);
                }
            }
        });
    }

    private void loadDiscoverableUsers() {
        FriendRepository.getInstance().getDiscoverableUsers(new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(List<UserProfile> users) {
                if (!isFinishing() && !isDestroyed() && searchAdapter != null) {
                    discoverableUsers.clear();
                    if (users != null) {
                        discoverableUsers.addAll(users);
                    }
                    String query = etSearchUsers != null ? etSearchUsers.getText().toString().trim() : "";
                    if (query.length() < 2) {
                        searchAdapter.setItems(discoverableUsers);
                        if (tvDiscoverHeader != null) {
                            tvDiscoverHeader.setText("Discover Gym Goers");
                        }
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing() && !isDestroyed() && searchAdapter != null) {
                    discoverableUsers.clear();
                }
            }
        });
    }

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private void setupSearch() {
        if (etSearchUsers != null) {
            etSearchUsers.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (searchRunnable != null) {
                        searchHandler.removeCallbacks(searchRunnable);
                    }

                    String query = s.toString().trim();
                    if (query.length() >= 2) {
                        if (tvRequestsHeader != null) tvRequestsHeader.setVisibility(View.GONE);
                        if (rvRequests != null) rvRequests.setVisibility(View.GONE);
                        if (tvDiscoverHeader != null) tvDiscoverHeader.setText("Search Results");

                        searchRunnable = () -> searchUsers(query);
                        searchHandler.postDelayed(searchRunnable, 300); // 300ms debounce
                    } else {
                        if (tvRequestsHeader != null) {
                            tvRequestsHeader.setVisibility(hasPendingRequests ? View.VISIBLE : View.GONE);
                        }
                        if (rvRequests != null) {
                            rvRequests.setVisibility(hasPendingRequests ? View.VISIBLE : View.GONE);
                        }
                        if (tvDiscoverHeader != null) tvDiscoverHeader.setText("Discover Gym Goers");
                        if (searchAdapter != null) {
                            searchAdapter.setItems(discoverableUsers);
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void searchUsers(String query) {
        FriendRepository.getInstance().searchUsersByUsername(query, new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(List<UserProfile> friends) {
                if (!isFinishing() && !isDestroyed() && searchAdapter != null) {
                    searchAdapter.setItems(friends);
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing() && !isDestroyed() && searchAdapter != null) {
                    searchAdapter.setItems(new ArrayList<>());
                }
            }
        });
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "GB";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        } else if (parts.length == 1 && !parts[0].isEmpty()) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return "GB";
    }

    // Pending Requests Adapter
    private class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.ViewHolder> {
        private final List<FriendRequest> list = new ArrayList<>();

        public void setItems(List<FriendRequest> items) {
            list.clear();
            if (items != null) list.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendRequest req = list.get(position);
            String senderUid = req.getSenderUid();

            if (senderUid != null) {
                holder.tvSenderName.setText("Loading...");
                holder.tvSenderUsername.setText("");
                holder.tvSenderAvatar.setText("?");

                UserRepository.getInstance().getUserProfile(senderUid, new UserRepository.ProfileCallback() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        if (!isFinishing() && !isDestroyed() && profile != null) {
                            String name = profile.getDisplayName() != null && !profile.getDisplayName().isEmpty()
                                    ? profile.getDisplayName()
                                    : (profile.getUsername() != null ? profile.getUsername() : "Gym Buddy");
                            String handle = profile.getUsername() != null ? "@" + profile.getUsername() : "";
                            
                            holder.tvSenderName.setText(name);
                            holder.tvSenderUsername.setText(handle);
                            holder.tvSenderAvatar.setText(getInitials(name));
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isFinishing() && !isDestroyed()) {
                            holder.tvSenderName.setText("Gym Buddy");
                            holder.tvSenderUsername.setText("@gymbuddy");
                            holder.tvSenderAvatar.setText("GB");
                        }
                    }
                });
            }

            holder.btnAccept.setOnClickListener(v -> {
                FriendRepository.getInstance().acceptFriendRequest(req.getRequestId(), new FriendRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        FriendRepository.getInstance().invalidateFriendsCache();
                        Toast.makeText(FriendRequestsActivity.this, "Friend request accepted! 🎉", Toast.LENGTH_SHORT).show();
                        loadPendingRequests();
                        loadDiscoverableUsers();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(FriendRequestsActivity.this, "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });

            holder.btnReject.setOnClickListener(v -> {
                FriendRepository.getInstance().rejectFriendRequest(req.getRequestId(), new FriendRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        FriendRepository.getInstance().invalidateFriendsCache();
                        Toast.makeText(FriendRequestsActivity.this, "Friend request declined", Toast.LENGTH_SHORT).show();
                        loadPendingRequests();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(FriendRequestsActivity.this, "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSenderAvatar;
            TextView tvSenderName;
            TextView tvSenderUsername;
            ImageButton btnAccept;
            ImageButton btnReject;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSenderAvatar = itemView.findViewById(R.id.tv_request_sender_avatar);
                tvSenderName = itemView.findViewById(R.id.tv_request_sender_name);
                tvSenderUsername = itemView.findViewById(R.id.tv_request_sender_username);
                btnAccept = itemView.findViewById(R.id.btn_accept_request);
                btnReject = itemView.findViewById(R.id.btn_reject_request);
            }
        }
    }

    // Search Users Adapter
    private class SearchUsersAdapter extends RecyclerView.Adapter<SearchUsersAdapter.ViewHolder> {
        private final List<UserProfile> list = new ArrayList<>();
        private final Set<String> sentRequestUids = new HashSet<>();

        public void setItems(List<UserProfile> items) {
            list.clear();
            if (items != null) list.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UserProfile user = list.get(position);
            String name = user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                    ? user.getDisplayName()
                    : (user.getUsername() != null ? user.getUsername() : "Gym Buddy");
            String username = user.getUsername() != null ? user.getUsername() : "";

            holder.tvName.setText(name);
            holder.tvUsername.setText("@" + username);
            holder.tvAvatar.setText(getInitials(name));

            int[] gradients = {
                    R.drawable.bg_avatar_gradient_1, R.drawable.bg_avatar_gradient_2,
                    R.drawable.bg_avatar_gradient_3, R.drawable.bg_avatar_gradient_4,
                    R.drawable.bg_avatar_gradient_5, R.drawable.bg_avatar_gradient_6
            };
            holder.tvAvatar.setBackgroundResource(gradients[Math.abs(position % gradients.length)]);

            String uid = user.getUid() != null ? user.getUid() : name;
            boolean isSent = sentRequestUids.contains(uid);

            if (isSent) {
                holder.btnAddFriend.setEnabled(false);
                holder.btnAddFriend.setBackgroundResource(R.drawable.bg_circle_action_dark);
                holder.btnAddFriend.setImageResource(R.drawable.ic_check);
            } else {
                holder.btnAddFriend.setEnabled(true);
                holder.btnAddFriend.setBackgroundResource(R.drawable.bg_circle_action_white);
                holder.btnAddFriend.setImageResource(R.drawable.ic_plus);
            }

            holder.btnAddFriend.setOnClickListener(v -> {
                holder.btnAddFriend.setEnabled(false);

                FriendRepository.getInstance().sendFriendRequest(uid, new FriendRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        sentRequestUids.add(uid);
                        Toast.makeText(FriendRequestsActivity.this, "Friend request sent to " + name + "!", Toast.LENGTH_SHORT).show();
                        holder.btnAddFriend.setBackgroundResource(R.drawable.bg_circle_action_dark);
                        holder.btnAddFriend.setImageResource(R.drawable.ic_check);
                    }

                    @Override
                    public void onError(Exception e) {
                        holder.btnAddFriend.setEnabled(true);
                        holder.btnAddFriend.setBackgroundResource(R.drawable.bg_circle_action_white);
                        holder.btnAddFriend.setImageResource(R.drawable.ic_plus);
                        Toast.makeText(FriendRequestsActivity.this, "Error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvAvatar;
            TextView tvName;
            TextView tvUsername;
            ImageButton btnAddFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAvatar = itemView.findViewById(R.id.tv_user_search_avatar);
                tvName = itemView.findViewById(R.id.tv_user_search_name);
                tvUsername = itemView.findViewById(R.id.tv_user_search_username);
                btnAddFriend = itemView.findViewById(R.id.btn_user_add_friend);
            }
        }
    }
}
