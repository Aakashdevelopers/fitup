package com.amstudio.fitup;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnSnapClickListener {
        void onSnapClick(ChatMessage message);
    }

    private static final int VIEW_TYPE_TEXT_INCOMING = 0;
    private static final int VIEW_TYPE_TEXT_OUTGOING = 1;
    private static final int VIEW_TYPE_SNAP_INCOMING = 2;
    private static final int VIEW_TYPE_SNAP_OUTGOING = 3;
    private static final int VIEW_TYPE_CALL_EVENT = 4;
    private static final int VIEW_TYPE_IMAGE_INCOMING = 5;
    private static final int VIEW_TYPE_IMAGE_OUTGOING = 6;

    private final List<ChatMessage> messageList = new ArrayList<>();
    private final OnSnapClickListener snapClickListener;

    public ChatMessageAdapter(OnSnapClickListener snapClickListener) {
        this.snapClickListener = snapClickListener;
    }

    public static Object getLocalFileOrTarget(String mediaUriStr, Context context) {
        if (mediaUriStr == null || mediaUriStr.isEmpty()) {
            return mediaUriStr;
        }

        try {
            if (mediaUriStr.startsWith("file://")) {
                Uri uri = Uri.parse(mediaUriStr);
                if (uri.getPath() != null) {
                    File file = new File(uri.getPath());
                    if (file.exists() && file.isFile()) {
                        return file;
                    }
                }
            } else if (mediaUriStr.startsWith("/")) {
                File file = new File(mediaUriStr);
                if (file.exists() && file.isFile()) {
                    return file;
                }
            } else if (mediaUriStr.startsWith("content://") && context != null) {
                Uri uri = Uri.parse(mediaUriStr);
                try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                    if (inputStream != null) {
                        return uri;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.w("ChatMessageAdapter", "Error resolving local media file: " + e.getMessage());
        }

        return mediaUriStr;
    }

    public void setMessages(List<ChatMessage> messages) {
        messageList.clear();
        if (messages != null) {
            messageList.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        if (message != null) {
            messageList.add(message);
            notifyItemInserted(messageList.size() - 1);
        }
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messageList.get(position);
        switch (message.getMessageType()) {
            case TEXT_OUTGOING:
                return VIEW_TYPE_TEXT_OUTGOING;
            case SNAP_INCOMING:
                return VIEW_TYPE_SNAP_INCOMING;
            case SNAP_OUTGOING:
                return VIEW_TYPE_SNAP_OUTGOING;
            case IMAGE_INCOMING:
                return VIEW_TYPE_IMAGE_INCOMING;
            case IMAGE_OUTGOING:
                return VIEW_TYPE_IMAGE_OUTGOING;
            case CALL_EVENT:
                return VIEW_TYPE_CALL_EVENT;
            case TEXT_INCOMING:
            default:
                return VIEW_TYPE_TEXT_INCOMING;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_TEXT_OUTGOING:
                View textOut = inflater.inflate(R.layout.item_message_text_outgoing, parent, false);
                return new TextMessageViewHolder(textOut);
            case VIEW_TYPE_SNAP_INCOMING:
                View snapIn = inflater.inflate(R.layout.item_message_snap_incoming, parent, false);
                return new SnapMessageViewHolder(snapIn);
            case VIEW_TYPE_SNAP_OUTGOING:
                View snapOut = inflater.inflate(R.layout.item_message_snap_outgoing, parent, false);
                return new SnapMessageViewHolder(snapOut);
            case VIEW_TYPE_IMAGE_OUTGOING:
                View imgOut = inflater.inflate(R.layout.item_message_image_outgoing, parent, false);
                return new ImageMessageViewHolder(imgOut);
            case VIEW_TYPE_IMAGE_INCOMING:
                View imgIn = inflater.inflate(R.layout.item_message_image_incoming, parent, false);
                return new ImageMessageViewHolder(imgIn);
            case VIEW_TYPE_CALL_EVENT:
                View callView = inflater.inflate(R.layout.item_message_call, parent, false);
                return new CallMessageViewHolder(callView);
            case VIEW_TYPE_TEXT_INCOMING:
            default:
                View textIn = inflater.inflate(R.layout.item_message_text_incoming, parent, false);
                return new TextMessageViewHolder(textIn);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        if (holder instanceof TextMessageViewHolder) {
            TextMessageViewHolder vh = (TextMessageViewHolder) holder;
            vh.tvMessageText.setText(message.getText());
            vh.tvMessageTime.setText(message.getTimestamp());
        } else if (holder instanceof SnapMessageViewHolder) {
            SnapMessageViewHolder vh = (SnapMessageViewHolder) holder;

            boolean isVideo = message.getSnapKind() == ChatMessage.SnapKind.VIDEO;
            vh.tvSnapLabel.setText(isVideo ? "Video Snap" : "Snap");
            vh.ivSnapIcon.setImageResource(isVideo ? R.drawable.ic_snap_video : R.drawable.ic_nav_camera);

            if (message.isOutgoing()) {
                vh.tvSnapSubtext.setText("Delivered • " + message.getDurationText());
            } else {
                vh.tvSnapSubtext.setText(message.isViewed() ? "Opened • Tap to replay" : "Tap to view • " + message.getDurationText());
            }

            vh.tvMessageTime.setText(message.getTimestamp());

            // Preload snap image silently for instant playback on click
            if (!isVideo && message.getMediaUri() != null && !message.getMediaUri().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(message.getMediaUri())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .preload();
            }

            vh.cardSnapBubble.setOnClickListener(v -> {
                if (snapClickListener != null) {
                    snapClickListener.onSnapClick(message);
                }
            });
        } else if (holder instanceof ImageMessageViewHolder) {
            ImageMessageViewHolder vh = (ImageMessageViewHolder) holder;
            vh.tvMessageTime.setText(message.getTimestamp());

            if (message.getMediaUri() != null && !message.getMediaUri().isEmpty()) {
                Object loadTarget = getLocalFileOrTarget(message.getMediaUri(), holder.itemView.getContext());
                Glide.with(holder.itemView.getContext())
                        .load(loadTarget)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.bg_avatar_gradient_3)
                        .into(vh.ivMessageImage);
            }

            vh.cardImageBubble.setOnClickListener(v -> {
                if (snapClickListener != null) {
                    snapClickListener.onSnapClick(message);
                }
            });
        } else if (holder instanceof CallMessageViewHolder) {
            CallMessageViewHolder vh = (CallMessageViewHolder) holder;
            vh.tvCallText.setText(message.getText());

            if (message.isMissedCall()) {
                vh.ivCallIcon.setImageResource(R.drawable.ic_missed_call);
                ImageViewCompat.setImageTintList(vh.ivCallIcon, ColorStateList.valueOf(Color.parseColor("#FF4D4F")));
            } else if (message.isVideoCall()) {
                vh.ivCallIcon.setImageResource(R.drawable.ic_video_call);
                ImageViewCompat.setImageTintList(vh.ivCallIcon, ColorStateList.valueOf(Color.parseColor("#38B6FF")));
            } else {
                vh.ivCallIcon.setImageResource(R.drawable.ic_phone_call);
                ImageViewCompat.setImageTintList(vh.ivCallIcon, ColorStateList.valueOf(Color.parseColor("#38B6FF")));
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class TextMessageViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessageText;
        final TextView tvMessageTime;

        TextMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageText = itemView.findViewById(R.id.tv_message_text);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }
    }

    static class SnapMessageViewHolder extends RecyclerView.ViewHolder {
        final View cardSnapBubble;
        final ImageView ivSnapIcon;
        final TextView tvSnapLabel;
        final TextView tvSnapSubtext;
        final TextView tvMessageTime;

        SnapMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardSnapBubble = itemView.findViewById(R.id.card_snap_bubble);
            ivSnapIcon = itemView.findViewById(R.id.iv_snap_icon);
            tvSnapLabel = itemView.findViewById(R.id.tv_snap_label);
            tvSnapSubtext = itemView.findViewById(R.id.tv_snap_subtext);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }
    }

    static class ImageMessageViewHolder extends RecyclerView.ViewHolder {
        final View cardImageBubble;
        final ImageView ivMessageImage;
        final TextView tvMessageTime;

        ImageMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardImageBubble = itemView.findViewById(R.id.card_image_bubble);
            ivMessageImage = itemView.findViewById(R.id.iv_message_image);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }
    }

    static class CallMessageViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivCallIcon;
        final TextView tvCallText;

        CallMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCallIcon = itemView.findViewById(R.id.iv_call_icon);
            tvCallText = itemView.findViewById(R.id.tv_call_text);
        }
    }
}
