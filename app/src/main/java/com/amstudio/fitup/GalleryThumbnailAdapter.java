package com.amstudio.fitup;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.model.GalleryItem;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class GalleryThumbnailAdapter extends RecyclerView.Adapter<GalleryThumbnailAdapter.ViewHolder> {

    public interface OnThumbnailClickListener {
        void onThumbnailClick(int position, GalleryItem item);
    }

    private final List<GalleryItem> items;
    private int selectedIndex = 0;
    private final OnThumbnailClickListener listener;

    public GalleryThumbnailAdapter(List<GalleryItem> items, int initialIndex, OnThumbnailClickListener listener) {
        this.items = items;
        this.selectedIndex = Math.max(0, Math.min(initialIndex, items.size() - 1));
        this.listener = listener;
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= items.size()) return;
        int previousIndex = selectedIndex;
        selectedIndex = index;
        notifyItemChanged(previousIndex);
        notifyItemChanged(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery_thumbnail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GalleryItem item = items.get(position);
        boolean isSelected = (position == selectedIndex);

        // Load thumbnail using Glide
        Glide.with(holder.itemView.getContext())
                .load(item.getUri())
                .centerCrop()
                .placeholder(R.drawable.bg_sample_gym_preview)
                .error(R.drawable.bg_sample_gym_preview)
                .into(holder.ivThumbnail);

        // Active selection styling
        if (isSelected) {
            holder.cardThumbnail.setStrokeWidth((int) (2.5f * parentDensity(holder)));
            holder.cardThumbnail.setStrokeColor(Color.WHITE);
        } else {
            holder.cardThumbnail.setStrokeWidth(0);
            holder.cardThumbnail.setStrokeColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                if (listener != null) {
                    listener.onThumbnailClick(currentPos, items.get(currentPos));
                }
            }
        });
    }

    private float parentDensity(ViewHolder holder) {
        return holder.itemView.getContext().getResources().getDisplayMetrics().density;
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardThumbnail;
        final ImageView ivThumbnail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardThumbnail = itemView.findViewById(R.id.card_thumbnail);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
        }
    }
}
