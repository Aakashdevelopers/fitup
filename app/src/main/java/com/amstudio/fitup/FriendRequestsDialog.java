package com.amstudio.fitup;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.FriendRequest;
import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.FriendRepository;
import com.amstudio.fitup.repository.UserRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FriendRequestsDialog extends DialogFragment {

    private ImageButton btnClose;
    private EditText etSearchUsers;
    private RecyclerView rvRequests;
    private RecyclerView rvSearchResults;
    private TextView tvRequestsHeader;

    private RequestsAdapter requestsAdapter;
    private SearchUsersAdapter searchAdapter;

    public static FriendRequestsDialog newInstance() {
        return new FriendRequestsDialog();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_friend_requests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnClose = view.findViewById(R.id.btn_requests_close);
        etSearchUsers = view.findViewById(R.id.et_search_users);
        rvRequests = view.findViewById(R.id.rv_pending_requests);
        rvSearchResults = view.findViewById(R.id.rv_user_search_results);
        tvRequestsHeader = view.findViewById(R.id.tv_requests_header);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        setupRecyclerViews();
        loadPendingRequests();
        setupSearch();
    }

    private void setupRecyclerViews() {
        if (rvRequests != null) {
            rvRequests.setLayoutManager(new LinearLayoutManager(getContext()));
            requestsAdapter = new RequestsAdapter();
            rvRequests.setAdapter(requestsAdapter);
        }

        if (rvSearchResults != null) {
            rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
            searchAdapter = new SearchUsersAdapter();
            rvSearchResults.setAdapter(searchAdapter);
        }
    }

    private void loadPendingRequests() {
        FriendRepository.getInstance().getPendingFriendRequests(new FriendRepository.FriendRequestsCallback() {
            @Override
            public void onSuccess(List<FriendRequest> requests) {
                if (isAdded() && requestsAdapter != null) {
                    requestsAdapter.setItems(requests);
                    if (tvRequestsHeader != null) {
                        tvRequestsHeader.setText(requests.isEmpty() ? "No pending friend requests" : "Pending Friend Requests (" + requests.size() + ")");
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded() && tvRequestsHeader != null) {
                    tvRequestsHeader.setText("No pending friend requests");
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
                        if (rvSearchResults != null) rvSearchResults.setVisibility(View.VISIBLE);
                        if (rvRequests != null) rvRequests.setVisibility(View.GONE);

                        searchRunnable = () -> searchUsers(query);
                        searchHandler.postDelayed(searchRunnable, 300); // 300ms debounce
                    } else {
                        if (rvSearchResults != null) rvSearchResults.setVisibility(View.GONE);
                        if (rvRequests != null) rvRequests.setVisibility(View.VISIBLE);
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
                if (isAdded() && searchAdapter != null) {
                    searchAdapter.setItems(friends);
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded() && searchAdapter != null) {
                    searchAdapter.setItems(new ArrayList<>());
                }
            }
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        return dialog;
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
                holder.tvSenderUid.setText("Loading...");
                UserRepository.getInstance().getUserProfile(senderUid, new UserRepository.ProfileCallback() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        if (isAdded() && profile != null) {
                            String name = profile.getDisplayName() != null && !profile.getDisplayName().isEmpty()
                                    ? profile.getDisplayName()
                                    : (profile.getUsername() != null ? profile.getUsername() : "Gym Buddy");
                            String handle = profile.getUsername() != null ? "@" + profile.getUsername() : "";
                            holder.tvSenderUid.setText(name + (handle.isEmpty() ? "" : " (" + handle + ")"));
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (isAdded()) {
                            holder.tvSenderUid.setText("Gym Buddy (" + senderUid.substring(0, Math.min(6, senderUid.length())) + ")");
                        }
                    }
                });
            }

            holder.btnAccept.setOnClickListener(v -> {
                FriendRepository.getInstance().acceptFriendRequest(req.getRequestId(), new FriendRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getContext(), "Friend request accepted! 🎉", Toast.LENGTH_SHORT).show();
                        loadPendingRequests();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(getContext(), "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });

            holder.btnReject.setOnClickListener(v -> {
                FriendRepository.getInstance().rejectFriendRequest(req.getRequestId(), new FriendRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getContext(), "Friend request declined", Toast.LENGTH_SHORT).show();
                        loadPendingRequests();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(getContext(), "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSenderUid;
            MaterialButton btnAccept;
            MaterialButton btnReject;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSenderUid = itemView.findViewById(R.id.tv_request_sender_name);
                btnAccept = itemView.findViewById(R.id.btn_accept_request);
                btnReject = itemView.findViewById(R.id.btn_reject_request);
            }
        }
    }

    // Search Users Adapter
    private class SearchUsersAdapter extends RecyclerView.Adapter<SearchUsersAdapter.ViewHolder> {
        private final List<UserProfile> list = new ArrayList<>();

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

            holder.btnAddFriend.setOnClickListener(v -> {
                holder.btnAddFriend.setEnabled(false);
                holder.btnAddFriend.setText("Sending...");

                FriendRepository.getInstance().sendFriendRequest(user.getUid(), new FriendRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getContext(), "Friend request sent to " + name + "!", Toast.LENGTH_SHORT).show();
                        holder.btnAddFriend.setText("Sent ✓");
                    }

                    @Override
                    public void onError(Exception e) {
                        holder.btnAddFriend.setEnabled(true);
                        holder.btnAddFriend.setText("Add");
                        Toast.makeText(getContext(), "Error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvUsername;
            MaterialButton btnAddFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_user_search_name);
                tvUsername = itemView.findViewById(R.id.tv_user_search_username);
                btnAddFriend = itemView.findViewById(R.id.btn_user_add_friend);
            }
        }
    }
}
