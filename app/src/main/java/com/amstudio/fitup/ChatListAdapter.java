package com.amstudio.fitup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {

    public interface OnChatItemClickListener {
        void onChatItemClick(ChatItem item);
        void onVoiceCallClick(ChatItem item);
        void onVideoCallClick(ChatItem item);
    }

    private final List<ChatItem> chatList = new ArrayList<>();
    private final OnChatItemClickListener clickListener;

    public ChatListAdapter(OnChatItemClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setItems(List<ChatItem> newItems) {
        chatList.clear();
        if (newItems != null) {
            chatList.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatItem item = chatList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvName.setText(item.getName());
        holder.tvTime.setText(item.getTime());

        // Last Message & Snap Type Setup
        if (item.getLastMessageType() == ChatItem.TYPE_PHOTO_SNAP) {
            holder.ivLastMessageIcon.setVisibility(View.VISIBLE);
            holder.ivLastMessageIcon.setImageResource(R.drawable.ic_nav_camera);
            holder.tvLastMessage.setText(item.getLastMessage().isEmpty() ? "Snap" : item.getLastMessage());
            holder.tvLastMessage.setTextColor(ContextCompat.getColor(context, R.color.electric_blue));
        } else if (item.getLastMessageType() == ChatItem.TYPE_VIDEO_SNAP) {
            holder.ivLastMessageIcon.setVisibility(View.VISIBLE);
            holder.ivLastMessageIcon.setImageResource(R.drawable.ic_snap_video);
            holder.tvLastMessage.setText(item.getLastMessage().isEmpty() ? "Video Snap" : item.getLastMessage());
            holder.tvLastMessage.setTextColor(ContextCompat.getColor(context, R.color.electric_blue));
        } else {
            holder.ivLastMessageIcon.setVisibility(View.GONE);
            holder.tvLastMessage.setText(item.getLastMessage());
            holder.tvLastMessage.setTextColor(ContextCompat.getColor(context, R.color.secondary_text));
        }

        // Avatar setup
        holder.tvInitials.setText(item.getInitials());
        holder.tvInitials.setBackgroundResource(item.getGradientResId());
        holder.viewOnlineDot.setVisibility(item.isOnline() ? View.VISIBLE : View.GONE);
        holder.viewAvatarRing.setVisibility(item.hasRing() ? View.VISIBLE : View.GONE);

        // Streak setup
        if (item.hasStreak()) {
            holder.layoutStreak.setVisibility(View.VISIBLE);
            holder.tvStreakCount.setText(String.valueOf(item.getStreakCount()));
        } else {
            holder.layoutStreak.setVisibility(View.GONE);
        }

        // Unread badge setup
        if (item.getUnreadCount() > 0) {
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
            holder.tvUnreadBadge.setText(String.valueOf(item.getUnreadCount()));
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onChatItemClick(item);
            }
        });

        if (holder.btnVoiceCall != null) {
            holder.btnVoiceCall.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onVoiceCallClick(item);
                }
            });
        }

        if (holder.btnVideoCall != null) {
            holder.btnVideoCall.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onVideoCallClick(item);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvInitials;
        final TextView tvName;
        final ImageView ivLastMessageIcon;
        final TextView tvLastMessage;
        final TextView tvTime;
        final TextView tvUnreadBadge;
        final TextView tvStreakCount;
        final View layoutStreak;
        final View viewOnlineDot;
        final View viewAvatarRing;
        final ImageButton btnVoiceCall;
        final ImageButton btnVideoCall;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitials = itemView.findViewById(R.id.tv_chat_avatar_initials);
            tvName = itemView.findViewById(R.id.tv_chat_name);
            ivLastMessageIcon = itemView.findViewById(R.id.iv_last_message_icon);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvTime = itemView.findViewById(R.id.tv_chat_time);
            tvUnreadBadge = itemView.findViewById(R.id.tv_unread_badge);
            tvStreakCount = itemView.findViewById(R.id.tv_streak_count);
            layoutStreak = itemView.findViewById(R.id.layout_streak);
            viewOnlineDot = itemView.findViewById(R.id.view_chat_online_dot);
            viewAvatarRing = itemView.findViewById(R.id.view_chat_avatar_ring);
            btnVoiceCall = itemView.findViewById(R.id.btn_item_voice_call);
            btnVideoCall = itemView.findViewById(R.id.btn_item_video_call);
        }
    }
}
