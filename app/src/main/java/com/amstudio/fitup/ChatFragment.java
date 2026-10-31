package com.amstudio.fitup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.FriendRequest;
import com.amstudio.fitup.model.UserProfile;
import com.amstudio.fitup.repository.FriendRepository;
import com.amstudio.fitup.repository.TencentChatManager;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.tencent.imsdk.v2.V2TIMConversation;
import com.tencent.imsdk.v2.V2TIMMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatFragment extends Fragment {

    private RecyclerView rvActiveFriends;
    private RecyclerView rvRecentChats;
    private EditText etSearch;
    private ImageButton btnSearchClear;
    private ImageButton btnHeaderNotification;
    private ImageButton btnHeaderAddFriend;
    private View badgeNotification;
    private View layoutEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private TextView btnFindFriends;
    private TextView tvRecentChatsHeader;
    private TextView tvNoActiveFriends;

    private ShimmerFrameLayout shimmerViewContainer;
    private View layoutContentContainer;

    private ActiveFriendsAdapter activeFriendsAdapter;
    private ChatListAdapter chatListAdapter;

    private final List<ActiveFriend> allActiveFriends = new ArrayList<>();
    private final List<ChatItem> allChats = new ArrayList<>();

    private final List<UserProfile> rawAcceptedFriends = new ArrayList<>();
    private final List<V2TIMConversation> rawConversations = new ArrayList<>();

    private boolean isFriendsLoaded = false;
    private boolean isChatsLoaded = false;

    public ChatFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerViews();
        setupSearchAndActions();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void initViews(View view) {
        rvActiveFriends = view.findViewById(R.id.rv_active_friends);
        rvRecentChats = view.findViewById(R.id.rv_recent_chats);
        etSearch = view.findViewById(R.id.et_search);
        btnSearchClear = view.findViewById(R.id.btn_search_clear);
        btnHeaderNotification = view.findViewById(R.id.btn_header_notification);
        btnHeaderAddFriend = view.findViewById(R.id.btn_header_add_friend);
        badgeNotification = view.findViewById(R.id.badge_notification);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        tvEmptyTitle = view.findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = view.findViewById(R.id.tv_empty_subtitle);
        btnFindFriends = view.findViewById(R.id.btn_find_friends);
        tvRecentChatsHeader = view.findViewById(R.id.tv_recent_chats_header);
        tvNoActiveFriends = view.findViewById(R.id.tv_no_active_friends);

        shimmerViewContainer = view.findViewById(R.id.shimmer_view_container);
        layoutContentContainer = view.findViewById(R.id.layout_content_container);
    }

    private void setupRecyclerViews() {
        // Horizontal Active Friends RecyclerView
        if (rvActiveFriends != null) {
            rvActiveFriends.setLayoutManager(
                    new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            activeFriendsAdapter = new ActiveFriendsAdapter(friend ->
                    openConversation(friend.getId(), friend.getName(), friend.getInitials(), friend.getGradientResId(), friend.isOnline()));
            rvActiveFriends.setAdapter(activeFriendsAdapter);
        }

        // Vertical Recent Chats & All Friends RecyclerView
        if (rvRecentChats != null) {
            rvRecentChats.setLayoutManager(new LinearLayoutManager(requireContext()));
            chatListAdapter = new ChatListAdapter(chat ->
                    openConversation(chat.getId(), chat.getName(), chat.getInitials(), chat.getGradientResId(), chat.isOnline()));
            rvRecentChats.setAdapter(chatListAdapter);
        }
    }

    private void openConversation(String id, String name, String initials, int gradientResId, boolean isOnline) {
        Intent intent = new Intent(requireContext(), IndividualChatActivity.class);
        intent.putExtra(IndividualChatActivity.EXTRA_CHAT_ID, id);
        intent.putExtra(IndividualChatActivity.EXTRA_CHAT_NAME, name);
        intent.putExtra(IndividualChatActivity.EXTRA_CHAT_INITIALS, initials);
        intent.putExtra(IndividualChatActivity.EXTRA_GRADIENT_RES, gradientResId);
        intent.putExtra(IndividualChatActivity.EXTRA_IS_ONLINE, isOnline);
        startActivity(intent);
    }

    private void showShimmer() {
        isFriendsLoaded = false;
        isChatsLoaded = false;
        if (shimmerViewContainer != null) {
            shimmerViewContainer.setVisibility(View.VISIBLE);
            shimmerViewContainer.startShimmer();
        }
        if (layoutContentContainer != null) {
            layoutContentContainer.setVisibility(View.GONE);
        }
    }

    private void checkAndHideShimmer() {
        if (isFriendsLoaded && isChatsLoaded) {
            if (shimmerViewContainer != null) {
                shimmerViewContainer.stopShimmer();
                shimmerViewContainer.setVisibility(View.GONE);
            }
            if (layoutContentContainer != null) {
                layoutContentContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadData() {
        showShimmer();

        // 0. Check pending friend requests to update notification badge
        loadPendingRequestsBadge();

        // 1. Fetch real accepted friends from Firestore
        FriendRepository.getInstance().getAcceptedFriends(new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(List<UserProfile> friends) {
                if (getActivity() == null || !isAdded()) return;

                rawAcceptedFriends.clear();
                if (friends != null) {
                    rawAcceptedFriends.addAll(friends);
                }

                isFriendsLoaded = true;
                combineAndRenderChatsAndFriends();
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;

                rawAcceptedFriends.clear();
                isFriendsLoaded = true;
                combineAndRenderChatsAndFriends();
            }
        });

        // 2. Fetch Tencent IM live conversations
        TencentChatManager chatManager = TencentChatManager.getInstance();
        chatManager.loginWithFirebaseUser(new TencentChatManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                chatManager.getConversationList(new TencentChatManager.ConversationListCallback() {
                    @Override
                    public void onSuccess(List<V2TIMConversation> conversations) {
                        if (getActivity() == null || !isAdded()) return;

                        rawConversations.clear();
                        if (conversations != null) {
                            rawConversations.addAll(conversations);
                        }

                        isChatsLoaded = true;
                        combineAndRenderChatsAndFriends();
                    }

                    @Override
                    public void onError(int code, String desc) {
                        if (getActivity() == null || !isAdded()) return;

                        rawConversations.clear();
                        isChatsLoaded = true;
                        combineAndRenderChatsAndFriends();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;

                rawConversations.clear();
                isChatsLoaded = true;
                combineAndRenderChatsAndFriends();
            }
        });
    }

    private void combineAndRenderChatsAndFriends() {
        if (!isFriendsLoaded || !isChatsLoaded) return;

        int[] gradients = {
                R.drawable.bg_avatar_gradient_1, R.drawable.bg_avatar_gradient_2,
                R.drawable.bg_avatar_gradient_3, R.drawable.bg_avatar_gradient_4,
                R.drawable.bg_avatar_gradient_5, R.drawable.bg_avatar_gradient_6
        };

        // 1. Active Friends Section (Horizontal List)
        allActiveFriends.clear();
        for (int i = 0; i < rawAcceptedFriends.size(); i++) {
            UserProfile user = rawAcceptedFriends.get(i);
            String name = user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                    ? user.getDisplayName()
                    : (user.getUsername() != null ? user.getUsername() : "Gym Buddy");
            String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "GB";
            int bgRes = gradients[i % gradients.length];
            allActiveFriends.add(new ActiveFriend(user.getUid(), name, initials, bgRes, true));
        }

        if (activeFriendsAdapter != null) {
            activeFriendsAdapter.setItems(allActiveFriends);
        }
        updateActiveFriendsUI(allActiveFriends);

        // 2. All Friends & Conversations Section (Vertical List)
        allChats.clear();
        Map<String, V2TIMConversation> convMap = new HashMap<>();
        for (V2TIMConversation conv : rawConversations) {
            String cId = conv.getUserID() != null ? conv.getUserID() : conv.getConversationID();
            if (cId != null) {
                convMap.put(cId, conv);
            }
        }

        Set<String> processedUids = new HashSet<>();

        // Include all accepted friends first so users can tap any friend to start chatting
        for (int i = 0; i < rawAcceptedFriends.size(); i++) {
            UserProfile user = rawAcceptedFriends.get(i);
            String uid = user.getUid();
            processedUids.add(uid);

            String name = user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                    ? user.getDisplayName()
                    : (user.getUsername() != null ? user.getUsername() : "Gym Buddy");
            String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "GB";
            int bgRes = gradients[i % gradients.length];

            V2TIMConversation conv = convMap.get(uid);
            String lastMsg = "Tap to start chatting 👋";
            String timeStr = "";
            int unread = 0;

            if (conv != null) {
                lastMsg = getMessageSummary(conv.getLastMessage());
                timeStr = "Just now";
                unread = conv.getUnreadCount();
            }

            allChats.add(new ChatItem(uid, name, initials, bgRes, lastMsg, timeStr, unread, true, 5, true, true, ChatItem.TYPE_TEXT));
        }

        // Add additional conversations with non-friends
        for (int i = 0; i < rawConversations.size(); i++) {
            V2TIMConversation conv = rawConversations.get(i);
            String convId = conv.getUserID() != null ? conv.getUserID() : conv.getConversationID();
            if (convId != null && !processedUids.contains(convId)) {
                String name = conv.getShowName() != null ? conv.getShowName() : "Gym Buddy";
                String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "GB";
                String lastMsg = getMessageSummary(conv.getLastMessage());
                int bgRes = gradients[(i + rawAcceptedFriends.size()) % gradients.length];
                int unread = conv.getUnreadCount();

                allChats.add(new ChatItem(convId, name, initials, bgRes, lastMsg, "Just now", unread, true, 5, true, true, ChatItem.TYPE_TEXT));
            }
        }

        if (chatListAdapter != null) {
            chatListAdapter.setItems(allChats);
        }

        updateEmptyState(allChats.isEmpty(), getSearchQuery());
        checkAndHideShimmer();
    }

    private String getSearchQuery() {
        return etSearch != null ? etSearch.getText().toString().trim() : "";
    }

    private String getMessageSummary(V2TIMMessage msg) {
        if (msg == null) return "Started a conversation";
        if (msg.getElemType() == V2TIMMessage.V2TIM_ELEM_TYPE_TEXT && msg.getTextElem() != null) {
            return msg.getTextElem().getText();
        }
        if (msg.getElemType() == V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM) {
            return "Private Snap";
        }
        return "New Message";
    }

    private void setupSearchAndActions() {
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterChatsAndFriends(s != null ? s.toString().trim() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        if (btnSearchClear != null) {
            btnSearchClear.setOnClickListener(v -> {
                if (etSearch != null) {
                    etSearch.setText("");
                }
            });
        }

        if (btnHeaderNotification != null) {
            btnHeaderNotification.setOnClickListener(v -> {
                FriendRequestsDialog dialog = FriendRequestsDialog.newInstance(false);
                dialog.setOnDismissListener(this::loadPendingRequestsBadge);
                dialog.show(getChildFragmentManager(), "FriendRequestsDialog");
            });
        }

        if (btnHeaderAddFriend != null) {
            btnHeaderAddFriend.setOnClickListener(v -> {
                FriendRequestsDialog dialog = FriendRequestsDialog.newInstance(true);
                dialog.setOnDismissListener(this::loadPendingRequestsBadge);
                dialog.show(getChildFragmentManager(), "FriendRequestsDialog");
            });
        }

        if (btnFindFriends != null) {
            btnFindFriends.setOnClickListener(v -> {
                if (etSearch != null && etSearch.getText().length() > 0) {
                    etSearch.setText("");
                } else {
                    Intent intent = new Intent(requireContext(), SelectFriendsActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    private void filterChatsAndFriends(String query) {
        boolean isSearching = !query.isEmpty();

        if (btnSearchClear != null) {
            btnSearchClear.setVisibility(isSearching ? View.VISIBLE : View.GONE);
        }

        List<ActiveFriend> filteredFriends = new ArrayList<>();
        List<ChatItem> filteredChats = new ArrayList<>();

        if (isSearching) {
            String lowerQuery = query.toLowerCase();
            for (ActiveFriend friend : allActiveFriends) {
                if (friend.getName().toLowerCase().contains(lowerQuery)) {
                    filteredFriends.add(friend);
                }
            }
            for (ChatItem chat : allChats) {
                if (chat.getName().toLowerCase().contains(lowerQuery) ||
                        chat.getLastMessage().toLowerCase().contains(lowerQuery)) {
                    filteredChats.add(chat);
                }
            }
        } else {
            filteredFriends.addAll(allActiveFriends);
            filteredChats.addAll(allChats);
        }

        if (activeFriendsAdapter != null) {
            activeFriendsAdapter.setItems(filteredFriends);
        }
        if (chatListAdapter != null) {
            chatListAdapter.setItems(filteredChats);
        }

        updateActiveFriendsUI(filteredFriends);
        updateEmptyState(filteredChats.isEmpty(), query);
    }

    private void updateActiveFriendsUI(List<ActiveFriend> friendsList) {
        boolean hasFriends = friendsList != null && !friendsList.isEmpty();

        if (rvActiveFriends != null) {
            rvActiveFriends.setVisibility(hasFriends ? View.VISIBLE : View.GONE);
        }
        if (tvNoActiveFriends != null) {
            tvNoActiveFriends.setVisibility(hasFriends ? View.GONE : View.VISIBLE);
        }
    }

    private void updateEmptyState(boolean isEmpty, String searchQuery) {
        if (layoutEmptyState == null) return;

        if (isEmpty) {
            if (rvRecentChats != null) rvRecentChats.setVisibility(View.GONE);
            if (tvRecentChatsHeader != null) tvRecentChatsHeader.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);

            if (tvEmptyTitle != null) {
                tvEmptyTitle.setText(R.string.no_friend_found);
            }

            if (tvEmptySubtitle != null) {
                if (!searchQuery.isEmpty()) {
                    tvEmptySubtitle.setText(getString(R.string.no_search_results_subtitle, searchQuery));
                } else {
                    tvEmptySubtitle.setText(R.string.empty_chats_subtitle);
                }
            }
        } else {
            if (rvRecentChats != null) rvRecentChats.setVisibility(View.VISIBLE);
            if (tvRecentChatsHeader != null) tvRecentChatsHeader.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void loadPendingRequestsBadge() {
        FriendRepository.getInstance().getPendingFriendRequests(new FriendRepository.FriendRequestsCallback() {
            @Override
            public void onSuccess(List<FriendRequest> requests) {
                if (getActivity() == null || !isAdded()) return;
                if (requests != null && !requests.isEmpty()) {
                    startBlinkingBadge(badgeNotification);
                } else {
                    stopBlinkingBadge(badgeNotification);
                }
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;
                stopBlinkingBadge(badgeNotification);
            }
        });
    }

    private void startBlinkingBadge(View view) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        if (view.getAnimation() == null) {
            AlphaAnimation blink = new AlphaAnimation(1.0f, 0.2f);
            blink.setDuration(600);
            blink.setInterpolator(new LinearInterpolator());
            blink.setRepeatCount(Animation.INFINITE);
            blink.setRepeatMode(Animation.REVERSE);
            view.startAnimation(blink);
        }
    }

    private void stopBlinkingBadge(View view) {
        if (view == null) return;
        view.clearAnimation();
        view.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        stopBlinkingBadge(badgeNotification);
        super.onDestroyView();
    }
}
