package com.amstudio.fitbond;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitbond.model.StreakReward;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class StreakRewardAdapter extends RecyclerView.Adapter<StreakRewardAdapter.RewardViewHolder> {

    public interface OnRewardClaimListener {
        void onClaimClick(StreakReward reward);
    }

    private final List<StreakReward> rewardList = new ArrayList<>();
    private final OnRewardClaimListener listener;

    public StreakRewardAdapter(OnRewardClaimListener listener) {
        this.listener = listener;
    }

    public void setRewards(List<StreakReward> rewards) {
        this.rewardList.clear();
        if (rewards != null) {
            this.rewardList.addAll(rewards);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RewardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_streak_reward, parent, false);
        return new RewardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RewardViewHolder holder, int position) {
        StreakReward reward = rewardList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvTitle.setText(reward.getTitle());
        holder.tvDesc.setText(reward.getDescription());

        if (reward.isClaimed()) {
            holder.btnClaim.setText("Claimed ✔");
            holder.btnClaim.setEnabled(false);
            holder.btnClaim.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.subtle_border));
            holder.btnClaim.setTextColor(ContextCompat.getColor(context, R.color.secondary_text));
            holder.ivBadge.setColorFilter(ContextCompat.getColor(context, R.color.online_green));
        } else if (reward.isUnlocked()) {
            holder.btnClaim.setText("Claim!");
            holder.btnClaim.setEnabled(true);
            holder.btnClaim.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.primary_blue));
            holder.btnClaim.setTextColor(ContextCompat.getColor(context, R.color.white));
            holder.ivBadge.setColorFilter(ContextCompat.getColor(context, R.color.streak_orange));
            holder.btnClaim.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClaimClick(reward);
                }
            });
        } else {
            holder.btnClaim.setText("Locked 🔒");
            holder.btnClaim.setEnabled(false);
            holder.btnClaim.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.profile_surface_variant));
            holder.btnClaim.setTextColor(ContextCompat.getColor(context, R.color.input_hint));
            holder.ivBadge.setColorFilter(ContextCompat.getColor(context, R.color.input_hint));
        }
    }

    @Override
    public int getItemCount() {
        return rewardList.size();
    }

    static class RewardViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBadge;
        TextView tvTitle;
        TextView tvDesc;
        MaterialButton btnClaim;

        public RewardViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBadge = itemView.findViewById(R.id.iv_reward_badge);
            tvTitle = itemView.findViewById(R.id.tv_reward_title);
            tvDesc = itemView.findViewById(R.id.tv_reward_desc);
            btnClaim = itemView.findViewById(R.id.btn_claim_reward);
        }
    }
}
