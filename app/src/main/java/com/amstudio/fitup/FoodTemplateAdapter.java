package com.amstudio.fitup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.svg.SvgTemplateManager;
import com.google.android.material.button.MaterialButton;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FoodTemplateAdapter extends RecyclerView.Adapter<FoodTemplateAdapter.TemplateViewHolder> {

    public interface OnTemplateSelectListener {
        void onTemplateSelected(int templateIndex);
        void onUseButtonClicked(int templateIndex);
    }

    private final OnTemplateSelectListener selectListener;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String foodTitle = "Grilled Chicken Bowl";
    private String foodSubtitle = "FitUp Kitchen · 350g portion";
    private String foodGrade = "A";
    private String kcal = "520 kcal";
    private String protein = "42g";
    private String carbs = "55g";
    private String fat = "12g";
    private @Nullable Uri foodImageUri;
    private @Nullable Bitmap foodImageBitmap;

    public FoodTemplateAdapter(OnTemplateSelectListener selectListener) {
        this.selectListener = selectListener;
    }

    public void updateFoodData(Context context, String title, String subtitle, String grade, String kcal, String protein, String carbs, String fat, @Nullable Uri imageUri) {
        this.foodTitle = title;
        this.foodSubtitle = subtitle;
        this.foodGrade = grade;
        this.kcal = kcal;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.foodImageUri = imageUri;

        if (imageUri != null) {
            executorService.execute(() -> {
                try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
                    Bitmap rawBitmap = BitmapFactory.decodeStream(is);
                    if (rawBitmap != null) {
                        // Downscale bitmap if too large to avoid OOM
                        Bitmap scaled = scaleBitmapDown(rawBitmap, 800);
                        mainHandler.post(() -> {
                            this.foodImageBitmap = scaled;
                            notifyDataSetChanged();
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(this::notifyDataSetChanged);
                }
            });
        } else {
            notifyDataSetChanged();
        }
    }

    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxDimension && height <= maxDimension) return bitmap;
        float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
        int newW = Math.round(width * ratio);
        int newH = Math.round(height * ratio);
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true);
    }

    public SvgTemplateManager.TemplateItem getTemplateItem(int index) {
        if (index >= 0 && index < SvgTemplateManager.TEMPLATES.length) {
            return SvgTemplateManager.TEMPLATES[index];
        }
        return SvgTemplateManager.TEMPLATES[0];
    }

    @NonNull
    @Override
    public TemplateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_template_carousel_card, parent, false);
        return new TemplateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TemplateViewHolder holder, int position) {
        SvgTemplateManager.TemplateItem item = SvgTemplateManager.TEMPLATES[position];

        if (holder.tvName != null) {
            holder.tvName.setText(item.name);
        }
        if (holder.tvDesc != null) {
            holder.tvDesc.setText(item.description);
        }

        // Render pure SVG template design asynchronously
        if (holder.ivSvgPreview != null) {
            final Context context = holder.itemView.getContext();
            final String assetPath = item.assetPath;
            final SvgTemplateManager.FoodData data = new SvgTemplateManager.FoodData(
                    foodTitle, kcal, protein, carbs, fat, "", null
            );

            executorService.execute(() -> {
                Bitmap rendered = SvgTemplateManager.renderTemplateToBitmap(
                        context, assetPath, data, 600, 800
                );
                mainHandler.post(() -> {
                    if (holder.getBindingAdapterPosition() == position && holder.ivSvgPreview != null) {
                        holder.ivSvgPreview.setImageBitmap(rendered);
                    }
                });
            });
        }

        if (holder.btnUseTemplate != null) {
            holder.btnUseTemplate.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (selectListener != null && pos != RecyclerView.NO_POSITION) {
                    selectListener.onUseButtonClicked(pos);
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (selectListener != null && pos != RecyclerView.NO_POSITION) {
                selectListener.onTemplateSelected(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return SvgTemplateManager.TEMPLATES.length;
    }

    public static class TemplateViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSvgPreview;
        TextView tvName;
        TextView tvDesc;
        MaterialButton btnUseTemplate;

        public TemplateViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSvgPreview = itemView.findViewById(R.id.iv_svg_card_preview);
            tvName = itemView.findViewById(R.id.tv_carousel_name);
            tvDesc = itemView.findViewById(R.id.tv_carousel_desc);
            btnUseTemplate = itemView.findViewById(R.id.btn_card_use_template);
        }
    }
}
