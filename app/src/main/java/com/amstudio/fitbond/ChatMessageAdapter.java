package com.amstudio.fitbond;

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
        default void onSnapClick(ChatMessage message, int startIndex) {
            onSnapClick(message);
        }
    }

    public interface OnMessageLongClickListener {
        void onMessageLongClick(ChatMessage message, View anchorView);
    }

    public interface OnReactionClickListener {
        void onReactionClick(ChatMessage message, String emoji);
    }

    private static final int VIEW_TYPE_TEXT_INCOMING = 0;
    private static final int VIEW_TYPE_TEXT_OUTGOING = 1;
    private static final int VIEW_TYPE_SNAP_INCOMING = 2;
    private static final int VIEW_TYPE_SNAP_OUTGOING = 3;
    private static final int VIEW_TYPE_CALL_EVENT = 4;
    private static final int VIEW_TYPE_IMAGE_INCOMING = 5;
    private static final int VIEW_TYPE_IMAGE_OUTGOING = 6;
    private static final int VIEW_TYPE_POST_SHARE_INCOMING = 7;
    private static final int VIEW_TYPE_POST_SHARE_OUTGOING = 8;

    private final List<ChatMessage> messageList = new ArrayList<>();
    private final OnSnapClickListener snapClickListener;
    private OnMessageLongClickListener messageLongClickListener;
    private OnReactionClickListener reactionClickListener;

    private String friendInitials = "GB";
    private int friendGradientRes = R.drawable.bg_avatar_gradient_3;

    public ChatMessageAdapter(OnSnapClickListener snapClickListener) {
        this.snapClickListener = snapClickListener;
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.messageLongClickListener = listener;
    }

    public void setOnReactionClickListener(OnReactionClickListener listener) {
        this.reactionClickListener = listener;
    }

    public void setFriendDetails(String initials, int gradientRes) {
        if (initials != null && !initials.isEmpty()) {
            this.friendInitials = initials;
        }
        if (gradientRes != 0) {
            this.friendGradientRes = gradientRes;
        }
        notifyDataSetChanged();
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
            case POST_SHARE_OUTGOING:
                return VIEW_TYPE_POST_SHARE_OUTGOING;
            case POST_SHARE_INCOMING:
                return VIEW_TYPE_POST_SHARE_INCOMING;
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
            case VIEW_TYPE_POST_SHARE_OUTGOING:
                View postOut = inflater.inflate(R.layout.item_message_post_share_outgoing, parent, false);
                return new PostShareMessageViewHolder(postOut);
            case VIEW_TYPE_POST_SHARE_INCOMING:
                View postIn = inflater.inflate(R.layout.item_message_post_share_incoming, parent, false);
                return new PostShareMessageViewHolder(postIn);
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

            if (vh.tvIncomingAvatar != null) {
                vh.tvIncomingAvatar.setText(friendInitials);
                vh.tvIncomingAvatar.setBackgroundResource(friendGradientRes);
            }

            bindMessageStatusIcon(vh.ivMessageStatus, message);

            View bubbleView = vh.layoutBubble != null ? vh.layoutBubble : vh.itemView;
            bubbleView.setOnLongClickListener(v -> {
                if (messageLongClickListener != null) {
                    messageLongClickListener.onMessageLongClick(message, v);
                    return true;
                }
                return false;
            });

            if (vh.layoutReactions != null) {
                vh.layoutReactions.removeAllViews();
                List<ChatMessage.Reaction> reactions = message.getReactions();
                if (reactions != null && !reactions.isEmpty()) {
                    vh.layoutReactions.setVisibility(View.VISIBLE);
                    LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
                    for (ChatMessage.Reaction r : reactions) {
                        View pillView = inflater.inflate(R.layout.item_reaction_pill, vh.layoutReactions, false);
                        TextView tvReaction = pillView.findViewById(R.id.tv_reaction_text);
                        if (tvReaction != null) {
                            tvReaction.setText(r.getDisplayText());
                            boolean isMyReaction = message.getUserReactionEmoji() != null && message.getUserReactionEmoji().equals(r.getEmoji());
                            tvReaction.setBackgroundResource(isMyReaction ? R.drawable.bg_reaction_pill_active : R.drawable.bg_reaction_pill);
                        }
                        pillView.setOnClickListener(v -> {
                            if (reactionClickListener != null) {
                                reactionClickListener.onReactionClick(message, r.getEmoji());
                            }
                        });
                        vh.layoutReactions.addView(pillView);
                    }
                } else {
                    vh.layoutReactions.setVisibility(View.GONE);
                }
            }
        } else if (holder instanceof SnapMessageViewHolder) {
            SnapMessageViewHolder vh = (SnapMessageViewHolder) holder;

            boolean isVideo = message.getSnapKind() == ChatMessage.SnapKind.VIDEO;
            vh.tvSnapLabel.setText(isVideo ? "Video Snap" : "Snap");
            vh.ivSnapIcon.setImageResource(isVideo ? R.drawable.ic_snap_video : R.drawable.ic_nav_camera);

            if (message.isOutgoing()) {
                if (message.getStatus() == ChatMessage.Status.READ) {
                    vh.tvSnapSubtext.setText("Opened • " + message.getDurationText());
                } else if (message.getStatus() == ChatMessage.Status.DELIVERED) {
                    vh.tvSnapSubtext.setText("Delivered • " + message.getDurationText());
                } else {
                    vh.tvSnapSubtext.setText("Sent • " + message.getDurationText());
                }
            } else {
                vh.tvSnapSubtext.setText(message.isViewed() ? "Opened • Tap to replay" : "Tap to view • " + message.getDurationText());
            }

            vh.tvMessageTime.setText(message.getTimestamp());
            bindMessageStatusIcon(vh.ivMessageStatus, message);

            if (vh.tvIncomingAvatar != null) {
                vh.tvIncomingAvatar.setText(friendInitials);
                vh.tvIncomingAvatar.setBackgroundResource(friendGradientRes);
            }

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

            vh.cardSnapBubble.setOnLongClickListener(v -> {
                if (messageLongClickListener != null) {
                    messageLongClickListener.onMessageLongClick(message, v);
                    return true;
                }
                return false;
            });
        } else if (holder instanceof ImageMessageViewHolder) {
            ImageMessageViewHolder vh = (ImageMessageViewHolder) holder;
            vh.tvMessageTime.setText(message.getTimestamp());

            if (vh.tvIncomingAvatar != null) {
                vh.tvIncomingAvatar.setText(friendInitials);
                vh.tvIncomingAvatar.setBackgroundResource(friendGradientRes);
            }

            bindMessageStatusIcon(vh.ivMessageStatus, message);

            List<String> uris = message.getMediaUris();
            int totalPhotos = message.getPhotoCount();
            if (uris != null && uris.size() > totalPhotos) {
                totalPhotos = uris.size();
            }
            if (totalPhotos < 1) {
                totalPhotos = 1;
            }

            String mainUri = (uris != null && !uris.isEmpty()) ? uris.get(0) : message.getMediaUri();

            if (mainUri != null && !mainUri.isEmpty()) {
                Object centerTarget = getLocalFileOrTarget(mainUri, holder.itemView.getContext());
                Glide.with(holder.itemView.getContext())
                        .load(centerTarget)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.bg_avatar_gradient_3)
                        .into(vh.ivMessageImage);
            }

            if (totalPhotos == 1) {
                // 1 Image -> Only 1 CardView
                if (vh.cardImageLeft != null) vh.cardImageLeft.setVisibility(View.GONE);
                if (vh.cardImageRight != null) vh.cardImageRight.setVisibility(View.GONE);
                if (vh.tvPhotoCountBadge != null) vh.tvPhotoCountBadge.setVisibility(View.GONE);

            } else if (totalPhotos == 2) {
                // 2 Images -> 2 CardViews
                if (vh.cardImageLeft != null) {
                    vh.cardImageLeft.setVisibility(View.VISIBLE);
                    String secondUri = (uris != null && uris.size() > 1) ? uris.get(1) : mainUri;
                    Object leftTarget = getLocalFileOrTarget(secondUri, holder.itemView.getContext());
                    Glide.with(holder.itemView.getContext())
                            .load(leftTarget)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.bg_avatar_gradient_3)
                            .into(vh.ivImageLeft);
                }
                if (vh.cardImageRight != null) vh.cardImageRight.setVisibility(View.GONE);
                if (vh.tvPhotoCountBadge != null) vh.tvPhotoCountBadge.setVisibility(View.GONE);

            } else if (totalPhotos == 3) {
                // 3 Images -> 3 CardViews
                if (vh.cardImageLeft != null) {
                    vh.cardImageLeft.setVisibility(View.VISIBLE);
                    String secondUri = (uris != null && uris.size() > 1) ? uris.get(1) : mainUri;
                    Object leftTarget = getLocalFileOrTarget(secondUri, holder.itemView.getContext());
                    Glide.with(holder.itemView.getContext())
                            .load(leftTarget)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.bg_avatar_gradient_3)
                            .into(vh.ivImageLeft);
                }
                if (vh.cardImageRight != null) {
                    vh.cardImageRight.setVisibility(View.VISIBLE);
                    String thirdUri = (uris != null && uris.size() > 2) ? uris.get(2) : mainUri;
                    Object rightTarget = getLocalFileOrTarget(thirdUri, holder.itemView.getContext());
                    Glide.with(holder.itemView.getContext())
                            .load(rightTarget)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.bg_avatar_gradient_3)
                            .into(vh.ivImageRight);
                }
                if (vh.tvPhotoCountBadge != null) vh.tvPhotoCountBadge.setVisibility(View.GONE);

            } else {
                // > 3 Images -> 3 CardViews + Photo Count Badge (+X photo)
                if (vh.cardImageLeft != null) {
                    vh.cardImageLeft.setVisibility(View.VISIBLE);
                    String secondUri = (uris != null && uris.size() > 1) ? uris.get(1) : mainUri;
                    Object leftTarget = getLocalFileOrTarget(secondUri, holder.itemView.getContext());
                    Glide.with(holder.itemView.getContext())
                            .load(leftTarget)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.bg_avatar_gradient_3)
                            .into(vh.ivImageLeft);
                }
                if (vh.cardImageRight != null) {
                    vh.cardImageRight.setVisibility(View.VISIBLE);
                    String thirdUri = (uris != null && uris.size() > 2) ? uris.get(2) : mainUri;
                    Object rightTarget = getLocalFileOrTarget(thirdUri, holder.itemView.getContext());
                    Glide.with(holder.itemView.getContext())
                            .load(rightTarget)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.bg_avatar_gradient_3)
                            .into(vh.ivImageRight);
                }
                if (vh.tvPhotoCountBadge != null) {
                    vh.tvPhotoCountBadge.setText("+" + totalPhotos + " photo");
                    vh.tvPhotoCountBadge.setVisibility(View.VISIBLE);
                }
            }

            // Reactions Binding
            if (vh.layoutReactions != null) {
                vh.layoutReactions.removeAllViews();
                List<ChatMessage.Reaction> reactions = message.getReactions();
                if (reactions != null && !reactions.isEmpty()) {
                    vh.layoutReactions.setVisibility(View.VISIBLE);
                    LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
                    for (ChatMessage.Reaction r : reactions) {
                        View pillView = inflater.inflate(R.layout.item_reaction_pill, vh.layoutReactions, false);
                        TextView tvReaction = pillView.findViewById(R.id.tv_reaction_text);
                        if (tvReaction != null) {
                            tvReaction.setText(r.getDisplayText());
                            boolean isMyReaction = message.getUserReactionEmoji() != null && message.getUserReactionEmoji().equals(r.getEmoji());
                            tvReaction.setBackgroundResource(isMyReaction ? R.drawable.bg_reaction_pill_active : R.drawable.bg_reaction_pill);
                        }
                        pillView.setOnClickListener(v -> {
                            if (reactionClickListener != null) {
                                reactionClickListener.onReactionClick(message, r.getEmoji());
                            }
                        });
                        vh.layoutReactions.addView(pillView);
                    }
                } else {
                    vh.layoutReactions.setVisibility(View.GONE);
                }
            }

            View.OnClickListener centerClickListener = v -> {
                if (snapClickListener != null) {
                    snapClickListener.onSnapClick(message, 0);
                }
            };

            View.OnClickListener leftClickListener = v -> {
                if (snapClickListener != null) {
                    int idx = (uris != null && uris.size() > 1) ? 1 : 0;
                    snapClickListener.onSnapClick(message, idx);
                }
            };

            View.OnClickListener rightClickListener = v -> {
                if (snapClickListener != null) {
                    int idx = (uris != null && uris.size() > 2) ? 2 : 0;
                    snapClickListener.onSnapClick(message, idx);
                }
            };

            vh.cardImageBubble.setOnClickListener(centerClickListener);
            if (vh.cardImageCenter != null) vh.cardImageCenter.setOnClickListener(centerClickListener);
            if (vh.ivMessageImage != null) vh.ivMessageImage.setOnClickListener(centerClickListener);

            if (vh.cardImageLeft != null) vh.cardImageLeft.setOnClickListener(leftClickListener);
            if (vh.ivImageLeft != null) vh.ivImageLeft.setOnClickListener(leftClickListener);

            if (vh.cardImageRight != null) vh.cardImageRight.setOnClickListener(rightClickListener);
            if (vh.ivImageRight != null) vh.ivImageRight.setOnClickListener(rightClickListener);

            View.OnLongClickListener longClickListener = v -> {
                if (messageLongClickListener != null) {
                    messageLongClickListener.onMessageLongClick(message, v);
                    return true;
                }
                return false;
            };

            vh.cardImageBubble.setOnLongClickListener(longClickListener);
            if (vh.cardImageCenter != null) vh.cardImageCenter.setOnLongClickListener(longClickListener);
            if (vh.ivMessageImage != null) vh.ivMessageImage.setOnLongClickListener(longClickListener);
            if (vh.cardImageLeft != null) vh.cardImageLeft.setOnLongClickListener(longClickListener);
            if (vh.ivImageLeft != null) vh.ivImageLeft.setOnLongClickListener(longClickListener);
            if (vh.cardImageRight != null) vh.cardImageRight.setOnLongClickListener(longClickListener);
            if (vh.ivImageRight != null) vh.ivImageRight.setOnLongClickListener(longClickListener);
        } else if (holder instanceof PostShareMessageViewHolder) {
            PostShareMessageViewHolder vh = (PostShareMessageViewHolder) holder;
            vh.tvMessageTime.setText(message.getTimestamp());
            if (vh.tvPostShareCaption != null) {
                vh.tvPostShareCaption.setText(message.getText());
            }

            if (message.getMediaUri() != null && !message.getMediaUri().isEmpty()) {
                Object loadTarget = getLocalFileOrTarget(message.getMediaUri(), holder.itemView.getContext());
                Glide.with(holder.itemView.getContext())
                        .load(loadTarget)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.bg_sample_gym_preview)
                        .into(vh.ivPostShareImage);
            }

            vh.cardPostShareBubble.setOnClickListener(v -> {
                Context ctx = holder.itemView.getContext();
                PostDetailActivity.open(
                        ctx,
                        message.getPostId() != null ? message.getPostId() : "",
                        message.getMediaUri(),
                        "",
                        message.getText(),
                        message.getSenderId()
                );
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

    private void bindMessageStatusIcon(ImageView ivMessageStatus, ChatMessage message) {
        if (ivMessageStatus == null) return;
        if (!message.isOutgoing()) {
            ivMessageStatus.setVisibility(View.GONE);
            return;
        }

        ivMessageStatus.setVisibility(View.VISIBLE);
        ChatMessage.Status status = message.getStatus();
        if (status == ChatMessage.Status.READ) {
            ivMessageStatus.setImageResource(R.drawable.ic_double_check);
            ImageViewCompat.setImageTintList(ivMessageStatus, ColorStateList.valueOf(Color.parseColor("#38B6FF")));
        } else if (status == ChatMessage.Status.DELIVERED) {
            ivMessageStatus.setImageResource(R.drawable.ic_double_check);
            ImageViewCompat.setImageTintList(ivMessageStatus, ColorStateList.valueOf(Color.parseColor("#8E8E93")));
        } else {
            ivMessageStatus.setImageResource(R.drawable.ic_single_check);
            ImageViewCompat.setImageTintList(ivMessageStatus, ColorStateList.valueOf(Color.parseColor("#8E8E93")));
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class TextMessageViewHolder extends RecyclerView.ViewHolder {
        final View layoutBubble;
        final TextView tvMessageText;
        final TextView tvMessageTime;
        final TextView tvIncomingAvatar;
        final ImageView ivMessageStatus;
        final ViewGroup layoutReactions;

        TextMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutBubble = itemView.findViewById(R.id.layout_bubble);
            tvMessageText = itemView.findViewById(R.id.tv_message_text);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
            tvIncomingAvatar = itemView.findViewById(R.id.tv_incoming_avatar);
            ivMessageStatus = itemView.findViewById(R.id.iv_message_status);
            layoutReactions = itemView.findViewById(R.id.layout_reactions);
        }
    }

    static class SnapMessageViewHolder extends RecyclerView.ViewHolder {
        final View cardSnapBubble;
        final ImageView ivSnapIcon;
        final TextView tvSnapLabel;
        final TextView tvSnapSubtext;
        final TextView tvMessageTime;
        final TextView tvIncomingAvatar;
        final ImageView ivMessageStatus;

        SnapMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardSnapBubble = itemView.findViewById(R.id.card_snap_bubble);
            ivSnapIcon = itemView.findViewById(R.id.iv_snap_icon);
            tvSnapLabel = itemView.findViewById(R.id.tv_snap_label);
            tvSnapSubtext = itemView.findViewById(R.id.tv_snap_subtext);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
            tvIncomingAvatar = itemView.findViewById(R.id.tv_incoming_avatar);
            ivMessageStatus = itemView.findViewById(R.id.iv_message_status);
        }
    }

    static class ImageMessageViewHolder extends RecyclerView.ViewHolder {
        final View cardImageBubble;
        final View cardImageLeft;
        final View cardImageRight;
        final View cardImageCenter;
        final ImageView ivMessageImage;
        final ImageView ivImageLeft;
        final ImageView ivImageRight;
        final TextView tvPhotoCountBadge;
        final TextView tvMessageTime;
        final ImageView ivMessageStatus;
        final TextView tvIncomingAvatar;
        final ViewGroup layoutReactions;

        ImageMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardImageBubble = itemView.findViewById(R.id.card_image_bubble);
            cardImageLeft = itemView.findViewById(R.id.card_image_left);
            cardImageRight = itemView.findViewById(R.id.card_image_right);
            cardImageCenter = itemView.findViewById(R.id.card_image_center);
            ivMessageImage = itemView.findViewById(R.id.iv_message_image);
            ivImageLeft = itemView.findViewById(R.id.iv_image_left);
            ivImageRight = itemView.findViewById(R.id.iv_image_right);
            tvPhotoCountBadge = itemView.findViewById(R.id.tv_photo_count_badge);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
            ivMessageStatus = itemView.findViewById(R.id.iv_message_status);
            tvIncomingAvatar = itemView.findViewById(R.id.tv_incoming_avatar);
            layoutReactions = itemView.findViewById(R.id.layout_reactions);
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

    static class PostShareMessageViewHolder extends RecyclerView.ViewHolder {
        final View cardPostShareBubble;
        final ImageView ivPostShareImage;
        final TextView tvPostShareCaption;
        final TextView tvMessageTime;

        PostShareMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPostShareBubble = itemView.findViewById(R.id.card_post_share_bubble);
            ivPostShareImage = itemView.findViewById(R.id.iv_post_share_image);
            tvPostShareCaption = itemView.findViewById(R.id.tv_post_share_caption);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }
    }
}
