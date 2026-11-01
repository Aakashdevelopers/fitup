package com.amstudio.fitup;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends AppCompatActivity {

    private static final String TAG = "ImageViewerActivity";

    public static final String EXTRA_IMAGE_URIS = "extra_image_uris";
    public static final String EXTRA_START_INDEX = "extra_start_index";
    public static final String EXTRA_SENDER_NAME = "extra_sender_name";

    private ViewPager2 vpImageViewer;
    private ImageButton btnClose;
    private ImageButton btnSave;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private TextView tvPhotoIndex;

    private final List<String> imageUris = new ArrayList<>();
    private int currentIndex = 0;
    private String senderName = "Photo";

    public static void open(Context context, ArrayList<String> uris, int startIndex, String senderName) {
        Intent intent = new Intent(context, ImageViewerActivity.class);
        intent.putStringArrayListExtra(EXTRA_IMAGE_URIS, uris);
        intent.putExtra(EXTRA_START_INDEX, startIndex);
        if (senderName != null) {
            intent.putExtra(EXTRA_SENDER_NAME, senderName);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindowFlags();
        setContentView(R.layout.activity_image_viewer);

        initViews();
        parseIntentData();
        setupViewPager();
        setupListeners();
    }

    private void setupWindowFlags() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
    }

    private void initViews() {
        vpImageViewer = findViewById(R.id.vp_image_viewer);
        btnClose = findViewById(R.id.btn_close_viewer);
        btnSave = findViewById(R.id.btn_save_photo);
        btnPrev = findViewById(R.id.btn_prev_photo);
        btnNext = findViewById(R.id.btn_next_photo);
        tvPhotoIndex = findViewById(R.id.tv_photo_index);
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_IMAGE_URIS)) {
                ArrayList<String> uris = intent.getStringArrayListExtra(EXTRA_IMAGE_URIS);
                if (uris != null && !uris.isEmpty()) {
                    imageUris.addAll(uris);
                }
            }
            currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);
            if (intent.hasExtra(EXTRA_SENDER_NAME)) {
                String name = intent.getStringExtra(EXTRA_SENDER_NAME);
                if (name != null && !name.isEmpty()) {
                    senderName = name;
                }
            }
        }

        if (imageUris.isEmpty()) {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (currentIndex < 0 || currentIndex >= imageUris.size()) {
            currentIndex = 0;
        }
    }

    private void setupViewPager() {
        ImageViewerAdapter adapter = new ImageViewerAdapter(imageUris);
        vpImageViewer.setAdapter(adapter);
        vpImageViewer.setCurrentItem(currentIndex, false);

        updateNavigationControls(currentIndex);

        vpImageViewer.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentIndex = position;
                updateNavigationControls(position);
            }
        });
    }

    private void updateNavigationControls(int position) {
        int total = imageUris.size();
        tvPhotoIndex.setText((position + 1) + " / " + total);

        if (total <= 1) {
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
        } else {
            btnPrev.setVisibility(position > 0 ? View.VISIBLE : View.GONE);
            btnNext.setVisibility(position < total - 1 ? View.VISIBLE : View.GONE);
        }
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> finish());

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                vpImageViewer.setCurrentItem(currentIndex - 1, true);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentIndex < imageUris.size() - 1) {
                vpImageViewer.setCurrentItem(currentIndex + 1, true);
            }
        });

        btnSave.setOnClickListener(v -> saveCurrentImageToGallery());
    }

    private void saveCurrentImageToGallery() {
        if (currentIndex < 0 || currentIndex >= imageUris.size()) return;

        String currentUriStr = imageUris.get(currentIndex);
        Object target = ChatMessageAdapter.getLocalFileOrTarget(currentUriStr, this);

        Toast.makeText(this, "Saving photo to Gallery...", Toast.LENGTH_SHORT).show();

        Glide.with(this)
                .asBitmap()
                .load(target)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                        saveBitmapToMediaStore(bitmap);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        Toast.makeText(ImageViewerActivity.this, "Failed to download image", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveBitmapToMediaStore(Bitmap bitmap) {
        if (bitmap == null) return;

        try {
            String filename = "FitUp_Photo_" + System.currentTimeMillis() + ".jpg";
            OutputStream fos;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FitUp");

                Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri != null) {
                    fos = getContentResolver().openOutputStream(imageUri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    if (fos != null) fos.close();
                    Toast.makeText(this, "Photo saved to Gallery! 🖼️", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show();
                }
            } else {
                String savedUriStr = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, filename, "Saved from FitUp Chat");
                if (savedUriStr != null) {
                    Toast.makeText(this, "Photo saved to Gallery! 🖼️", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving photo to MediaStore", e);
            Toast.makeText(this, "Save error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static class ImageViewerAdapter extends RecyclerView.Adapter<ImageViewerAdapter.ViewHolder> {

        private final List<String> uris;

        ImageViewerAdapter(List<String> uris) {
            this.uris = uris != null ? uris : new ArrayList<>();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_zoomable_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String uriStr = uris.get(position);
            Object target = ChatMessageAdapter.getLocalFileOrTarget(uriStr, holder.itemView.getContext());

            Glide.with(holder.itemView.getContext())
                    .load(target)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_avatar_gradient_3)
                    .into(holder.zoomableImageView);
        }

        @Override
        public int getItemCount() {
            return uris.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ZoomableImageView zoomableImageView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                zoomableImageView = itemView.findViewById(R.id.zoomable_image_view);
            }
        }
    }
}
