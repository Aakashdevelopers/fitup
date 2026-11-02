package com.amstudio.fitbond;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.amstudio.fitbond.svg.SvgTemplateManager;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FoodSnapImageViewerDialog extends DialogFragment {

    private static final String TAG = "FoodSnapImageViewerDialog";

    private static final String ARG_FOOD_IMAGE_URI = "ARG_FOOD_IMAGE_URI";
    private static final String ARG_TEMPLATE_INDEX = "ARG_TEMPLATE_INDEX";
    private static final String ARG_FOOD_TITLE = "ARG_FOOD_TITLE";
    private static final String ARG_KCAL = "ARG_KCAL";
    private static final String ARG_PROTEIN = "ARG_PROTEIN";
    private static final String ARG_CARBS = "ARG_CARBS";
    private static final String ARG_FAT = "ARG_FAT";

    private @Nullable Uri foodImageUri;
    private int templateIndex = 0;
    private String foodTitle = "";
    private String kcal = "";
    private String protein = "";
    private String carbs = "";
    private String fat = "";

    private ZoomableImageView zoomableImageView;
    private ProgressBar pbLoading;
    private MaterialButton btnToggleOverlay;
    private TextView tvViewerSubtitle;

    private @Nullable Bitmap rawFoodBitmap;
    private @Nullable Bitmap compositeOverlayBitmap;
    private boolean isOverlayVisible = true;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static FoodSnapImageViewerDialog newInstance(
            @Nullable String foodImageUriStr,
            int templateIndex,
            String foodTitle,
            String kcal,
            String protein,
            String carbs,
            String fat) {

        FoodSnapImageViewerDialog dialog = new FoodSnapImageViewerDialog();
        Bundle args = new Bundle();
        if (foodImageUriStr != null) args.putString(ARG_FOOD_IMAGE_URI, foodImageUriStr);
        args.putInt(ARG_TEMPLATE_INDEX, templateIndex);
        args.putString(ARG_FOOD_TITLE, foodTitle);
        args.putString(ARG_KCAL, kcal);
        args.putString(ARG_PROTEIN, protein);
        args.putString(ARG_CARBS, carbs);
        args.putString(ARG_FAT, fat);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_food_snap_image_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        zoomableImageView = view.findViewById(R.id.zoomable_food_viewer);
        pbLoading = view.findViewById(R.id.pb_image_viewer_loading);
        btnToggleOverlay = view.findViewById(R.id.btn_toggle_overlay);
        tvViewerSubtitle = view.findViewById(R.id.tv_viewer_subtitle);

        TextView tvTitle = view.findViewById(R.id.tv_viewer_title);
        ImageButton btnClose = view.findViewById(R.id.btn_close_image_viewer);
        ImageButton btnSave = view.findViewById(R.id.btn_save_food_image);
        MaterialButton btnSend = view.findViewById(R.id.btn_send_from_viewer);

        parseArguments();

        if (tvTitle != null && !foodTitle.isEmpty()) {
            tvTitle.setText(foodTitle + " 🥗");
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        if (btnToggleOverlay != null) {
            btnToggleOverlay.setOnClickListener(v -> toggleOverlayMode());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveDisplayedImageToGallery());
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendSnapFromViewer());
        }

        renderAndLoadImages();
    }

    private void parseArguments() {
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(ARG_FOOD_IMAGE_URI)) {
                String uriStr = args.getString(ARG_FOOD_IMAGE_URI);
                if (uriStr != null) foodImageUri = Uri.parse(uriStr);
            }
            templateIndex = args.getInt(ARG_TEMPLATE_INDEX, 0);
            foodTitle = args.getString(ARG_FOOD_TITLE, "Food Snap");
            kcal = args.getString(ARG_KCAL, "520 kcal");
            protein = args.getString(ARG_PROTEIN, "42g");
            carbs = args.getString(ARG_CARBS, "55g");
            fat = args.getString(ARG_FAT, "12g");
        }
    }

    private void renderAndLoadImages() {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);

        Context context = getContext();
        if (context == null) return;

        executorService.execute(() -> {
            try {
                // Decode raw food bitmap
                if (foodImageUri != null) {
                    try (InputStream is = context.getContentResolver().openInputStream(foodImageUri)) {
                        rawFoodBitmap = BitmapFactory.decodeStream(is);
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding raw food image", e);
                    }
                }

                SvgTemplateManager.FoodData foodData = new SvgTemplateManager.FoodData(
                        foodTitle, kcal, protein, carbs, fat, "", rawFoodBitmap
                );

                // Render high resolution composite image (1080x1920)
                compositeOverlayBitmap = SvgTemplateManager.renderFullFoodSnapWithOverlay(
                        context, foodImageUri, rawFoodBitmap, templateIndex, foodData, null, 1080, 1920
                );

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                    if (zoomableImageView != null && compositeOverlayBitmap != null) {
                        zoomableImageView.setImageBitmap(compositeOverlayBitmap);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to render composite food image", e);
                mainHandler.post(() -> {
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed to load image viewer", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void toggleOverlayMode() {
        isOverlayVisible = !isOverlayVisible;
        if (zoomableImageView == null) return;

        if (isOverlayVisible) {
            if (compositeOverlayBitmap != null) {
                zoomableImageView.setImageBitmap(compositeOverlayBitmap);
            }
            if (btnToggleOverlay != null) {
                btnToggleOverlay.setText("👁️ Template Overlay: ON");
            }
            if (tvViewerSubtitle != null) {
                tvViewerSubtitle.setText("Pinch or double-tap to zoom • Template Overlay Active");
            }
        } else {
            if (rawFoodBitmap != null) {
                zoomableImageView.setImageBitmap(rawFoodBitmap);
            }
            if (btnToggleOverlay != null) {
                btnToggleOverlay.setText("🙈 Template Overlay: OFF");
            }
            if (tvViewerSubtitle != null) {
                tvViewerSubtitle.setText("Viewing Raw Food Photo • Overlay Hidden");
            }
        }
    }

    private void saveDisplayedImageToGallery() {
        Bitmap targetBitmap = isOverlayVisible ? compositeOverlayBitmap : rawFoodBitmap;
        if (targetBitmap == null) {
            Toast.makeText(getContext(), "No image available to save", Toast.LENGTH_SHORT).show();
            return;
        }

        Context context = getContext();
        if (context == null) return;

        Toast.makeText(context, "Saving photo to Gallery...", Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            try {
                String filename = "FitBond_FoodSnap_" + System.currentTimeMillis() + ".jpg";
                OutputStream fos;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FitBond");

                    Uri imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (imageUri != null) {
                        fos = context.getContentResolver().openOutputStream(imageUri);
                        targetBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                        if (fos != null) fos.close();
                        mainHandler.post(() -> Toast.makeText(context, "Saved to Gallery! 🖼️", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    String savedUriStr = MediaStore.Images.Media.insertImage(context.getContentResolver(), targetBitmap, filename, "Saved from FitUp Food Snap Viewer");
                    if (savedUriStr != null) {
                        mainHandler.post(() -> Toast.makeText(context, "Saved to Gallery! 🖼️", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving food image to MediaStore", e);
                mainHandler.post(() -> Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendSnapFromViewer() {
        Bitmap snapBitmap = compositeOverlayBitmap != null ? compositeOverlayBitmap : rawFoodBitmap;
        if (snapBitmap == null || getContext() == null) return;

        Context context = getContext();
        executorService.execute(() -> {
            try {
                File cacheDir = new File(context.getCacheDir(), "food_snaps");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                File snapFile = new File(cacheDir, "food_snap_viewer_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(snapFile)) {
                    snapBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    fos.flush();
                }

                Uri snapUri = Uri.fromFile(snapFile);

                mainHandler.post(() -> {
                    dismiss();
                    Intent intent = new Intent(context, SelectFriendsActivity.class);
                    intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, snapUri.toString());
                    intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_PHOTO);
                    startActivity(intent);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to capture and send snap from viewer", e);
                mainHandler.post(() -> Toast.makeText(context, "Failed to send snap", Toast.LENGTH_SHORT).show());
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executorService.shutdown();
    }
}
