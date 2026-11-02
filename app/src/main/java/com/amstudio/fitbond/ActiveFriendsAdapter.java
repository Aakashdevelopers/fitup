package com.amstudio.fitbond;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ActiveFriendsAdapter extends RecyclerView.Adapter<ActiveFriendsAdapter.ViewHolder> {

    public interface OnActiveFriendClickListener {
        void onActiveFriendClick(ActiveFriend friend);
    }

    private final List<ActiveFriend> friendList = new ArrayList<>();
    private final OnActiveFriendClickListener clickListener;

    public ActiveFriendsAdapter(OnActiveFriendClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setItems(List<ActiveFriend> newItems) {
        friendList.clear();
        if (newItems != null) {
            friendList.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_active_friend, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActiveFriend friend = friendList.get(position);

        holder.tvName.setText(friend.getName());
        holder.tvInitials.setText(friend.getInitials());
        holder.tvInitials.setBackgroundResource(friend.getGradientResId());

        holder.viewOnlineIndicator.setVisibility(friend.isOnline() ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onActiveFriendClick(friend);
            }
        });
    }

    @Override
    public int getItemCount() {
        return friendList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvInitials;
        final TextView tvName;
        final View viewOnlineIndicator;
        final View viewAvatarRing;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitials = itemView.findViewById(R.id.tv_active_avatar_initials);
            tvName = itemView.findViewById(R.id.tv_active_friend_name);
            viewOnlineIndicator = itemView.findViewById(R.id.view_online_indicator);
            viewAvatarRing = itemView.findViewById(R.id.view_avatar_ring);
        }
    }
}
