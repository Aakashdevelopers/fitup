package com.amstudio.fitbond;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class EditPostActivity extends AppCompatActivity {

    private static final String TAG = "EditPostActivity";

    private ImageButton btnBack;
    private View layoutAudioPill;
    private TextView tvAudioTitle;
    private ImageView ivMainPreview;
    private GridOverlayView gridOverlayView;
    private TextView tvTextOverlay;

    private View btnToolAudio;
    private View btnToolText;
    private View btnToolOverlay;
    private View btnToolFilter;
    private View btnToolEdit;
    private View btnToolRatio;

    private ImageView ivBottomThumbnail;
    private View btnAddMore;
    private MaterialButton btnNextShare;

    private View containerImageOverlay;
    private ImageView ivOverlayPicture;
    private ImageButton btnDeleteOverlay;
    private SeekBar sbVerticalFontSize;

    private String currentTextCaption = "";
    private int currentTextColor = Color.WHITE;
    private boolean currentHasFill = true;
    private int currentTextAlignment = Gravity.CENTER;
    private float currentTextSizeSp = 22f;
    private ScaleGestureDetector scaleGestureDetector;

    private Uri mediaUri;
    private Uri overlayMediaUri;
    private final List<Uri> multipleUris = new ArrayList<>();
    private int currentFilterIndex = 0;
    private float[] currentFilterMatrix = null;
    private boolean isCropSquare = true;

    private float currentBrightness = 0f;
    private float currentContrast = 1.0f;
    private float currentSaturation = 1.0f;
    private boolean isGridVisible = false;
    private RatioBottomSheetDialog.RatioMode currentRatioMode = RatioBottomSheetDialog.RatioMode.SQUARE;

    private ActivityResultLauncher<PickVisualMediaRequest> overlayPickerLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> addMorePhotosLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_post);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        View rootLayout = findViewById(R.id.root_edit_post);
        View statusBarSpacer = findViewById(R.id.view_status_bar_spacer);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (statusBarSpacer != null) {
                    ViewGroup.LayoutParams lp = statusBarSpacer.getLayoutParams();
                    if (lp != null) {
                        lp.height = systemBars.top;
                        statusBarSpacer.setLayoutParams(lp);
                    }
                }
                return WindowInsetsCompat.CONSUMED;
            });
        }

        parseIntentData();
        initViews();
        initOverlayPickerLauncher();
        initAddMorePhotosLauncher();
        setupClickListeners();
        loadMediaPreview();
    }

    private void initAddMorePhotosLauncher() {
        addMorePhotosLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        if (!multipleUris.contains(uri)) {
                            multipleUris.add(uri);
                        }
                        if (mediaUri != null && !multipleUris.contains(mediaUri)) {
                            multipleUris.add(mediaUri);
                        }
                        mediaUri = uri;
                        loadMediaPreview();
                        Toast.makeText(this, "New photo added (" + multipleUris.size() + " total)", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void initOverlayPickerLauncher() {
        overlayPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        overlayMediaUri = uri;
                        showOverlayPicture(uri);
                    }
                }
        );
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            String uriStr = intent.getStringExtra(SnapPreviewActivity.EXTRA_MEDIA_URI);
            if (uriStr != null) {
                mediaUri = Uri.parse(uriStr);
            } else if (intent.getParcelableExtra(SnapPreviewActivity.EXTRA_MEDIA_URI) != null) {
                mediaUri = intent.getParcelableExtra(SnapPreviewActivity.EXTRA_MEDIA_URI);
            }

            ArrayList<Uri> extraUris = intent.getParcelableArrayListExtra("extra_multiple_uris");
            if (extraUris != null) {
                multipleUris.addAll(extraUris);
                if (mediaUri == null && !multipleUris.isEmpty()) {
                    mediaUri = multipleUris.get(0);
                }
            }
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back_edit_post);
        layoutAudioPill = findViewById(R.id.layout_audio_pill);
        tvAudioTitle = findViewById(R.id.tv_audio_title);
        ivMainPreview = findViewById(R.id.iv_edit_main_preview);
        gridOverlayView = findViewById(R.id.grid_overlay_view);
        tvTextOverlay = findViewById(R.id.tv_text_overlay);

        btnToolAudio = findViewById(R.id.btn_tool_audio);
        btnToolText = findViewById(R.id.btn_tool_text);
        btnToolOverlay = findViewById(R.id.btn_tool_overlay);
        btnToolFilter = findViewById(R.id.btn_tool_filter);
        btnToolEdit = findViewById(R.id.btn_tool_edit);
        btnToolRatio = findViewById(R.id.btn_tool_ratio);

        ivBottomThumbnail = findViewById(R.id.iv_bottom_thumbnail);
        btnAddMore = findViewById(R.id.btn_add_more);
        btnNextShare = findViewById(R.id.btn_next_share);

        containerImageOverlay = findViewById(R.id.container_image_overlay);
        ivOverlayPicture = findViewById(R.id.iv_overlay_picture);
        btnDeleteOverlay = findViewById(R.id.btn_delete_overlay);
        sbVerticalFontSize = findViewById(R.id.sb_vertical_font_size);
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (layoutAudioPill != null) {
            layoutAudioPill.setOnClickListener(v -> selectAudioTrack());
        }

        if (btnToolAudio != null) {
            btnToolAudio.setOnClickListener(v -> selectAudioTrack());
        }

        if (btnToolText != null) {
            btnToolText.setOnClickListener(v -> openTextEditDialog());
        }

        setupTextOverlayDragAndZoom();
        setupVerticalFontSizeSeekBar();

        if (btnToolOverlay != null) {
            btnToolOverlay.setOnClickListener(v -> openOverlayPhotoPicker());
        }

        if (btnDeleteOverlay != null) {
            btnDeleteOverlay.setOnClickListener(v -> removeOverlayPicture());
        }

        setupOverlayDragListener();

        if (btnToolFilter != null) {
            btnToolFilter.setOnClickListener(v -> openImageFilterDialog());
        }

        if (btnToolEdit != null) {
            btnToolEdit.setOnClickListener(v -> openImageAdjustDialog());
        }

        if (btnToolRatio != null) {
            btnToolRatio.setOnClickListener(v -> openRatioDialog());
        }

        if (btnAddMore != null) {
            btnAddMore.setOnClickListener(v -> openAddMorePhotosPicker());
        }

        if (btnNextShare != null) {
            btnNextShare.setOnClickListener(v -> proceedToShare());
        }
    }

    private void loadMediaPreview() {
        if (mediaUri != null) {
            if (ivMainPreview != null) {
                Glide.with(this)
                        .load(mediaUri)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(ivMainPreview);
            }

            if (ivBottomThumbnail != null) {
                Glide.with(this)
                        .load(mediaUri)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(ivBottomThumbnail);
            }
        }
    }

    private void selectAudioTrack() {
        AudioSelectionBottomSheetDialog dialog = AudioSelectionBottomSheetDialog.newInstance();
        dialog.setOnAudioSelectedListener((trackName, artistName, audioUri) -> {
            String fullTitle = trackName + " • " + artistName;
            if (tvAudioTitle != null) {
                tvAudioTitle.setText(fullTitle);
            }
            Toast.makeText(EditPostActivity.this, "Audio selected: " + fullTitle, Toast.LENGTH_SHORT).show();
        });
        dialog.show(getSupportFragmentManager(), "AudioSelectionBottomSheetDialog");
    }

    private void openTextEditDialog() {
        if (sbVerticalFontSize != null) {
            sbVerticalFontSize.setVisibility(View.VISIBLE);
        }

        TextEditBottomSheetDialog dialog = TextEditBottomSheetDialog.newInstance(
                currentTextCaption, currentTextColor, currentHasFill, currentTextAlignment
        );
        dialog.setOnTextEditListener(new TextEditBottomSheetDialog.OnTextEditListener() {
            @Override
            public void onTextChanged(String text, int textColor, boolean hasFillBackground, int alignment) {
                applyTextFormatting(text, textColor, hasFillBackground, alignment);
            }

            @Override
            public void onDone(String text, int textColor, boolean hasFillBackground, int alignment) {
                applyTextFormatting(text, textColor, hasFillBackground, alignment);
            }
        });
        dialog.show(getSupportFragmentManager(), "TextEditBottomSheetDialog");
    }

    private void applyTextFormatting(String text, int textColor, boolean hasFillBackground, int alignment) {
        currentTextCaption = text;
        currentTextColor = textColor;
        currentHasFill = hasFillBackground;
        currentTextAlignment = alignment;

        if (tvTextOverlay != null) {
            if (!TextUtils.isEmpty(text)) {
                tvTextOverlay.setText(text);
                tvTextOverlay.setTextColor(textColor);
                tvTextOverlay.setGravity(alignment);
                tvTextOverlay.setBackgroundColor(hasFillBackground ? Color.parseColor("#90000000") : Color.TRANSPARENT);
                tvTextOverlay.setVisibility(View.VISIBLE);
            } else {
                tvTextOverlay.setVisibility(View.GONE);
            }
        }
    }

    private void setupVerticalFontSizeSeekBar() {
        if (sbVerticalFontSize == null) return;

        sbVerticalFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentTextSizeSp = 14f + progress; // range 14sp to 64sp
                if (tvTextOverlay != null) {
                    tvTextOverlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSizeSp);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupTextOverlayDragAndZoom() {
        if (tvTextOverlay == null) return;

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                currentTextSizeSp = Math.max(12f, Math.min(72f, currentTextSizeSp * scaleFactor));
                if (tvTextOverlay != null) {
                    tvTextOverlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSizeSp);
                }
                if (sbVerticalFontSize != null) {
                    sbVerticalFontSize.setProgress((int) Math.max(0, currentTextSizeSp - 14f));
                }
                return true;
            }
        });

        tvTextOverlay.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (scaleGestureDetector != null) {
                    scaleGestureDetector.onTouchEvent(event);
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (!scaleGestureDetector.isInProgress()) {
                            view.animate()
                                    .x(event.getRawX() + dX)
                                    .y(event.getRawY() + dY)
                                    .setDuration(0)
                                    .start();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void openImageFilterDialog() {
        ImageFilterBottomSheetDialog dialog = ImageFilterBottomSheetDialog.newInstance(
                currentFilterIndex, mediaUri
        );
        dialog.setOnFilterSelectedListener(new ImageFilterBottomSheetDialog.OnFilterSelectedListener() {
            @Override
            public void onFilterSelected(int filterIndex, String filterName, float[] matrix) {
                applyFilterMatrix(matrix);
            }

            @Override
            public void onDone(int selectedIndex) {
                currentFilterIndex = selectedIndex;
            }

            @Override
            public void onCancel() {
                applyFilterMatrix(currentFilterMatrix);
            }
        });
        dialog.show(getSupportFragmentManager(), "ImageFilterBottomSheetDialog");
    }

    private void applyFilterMatrix(float[] matrix) {
        currentFilterMatrix = matrix;
        if (ivMainPreview != null) {
            if (matrix != null) {
                ivMainPreview.setColorFilter(new ColorMatrixColorFilter(matrix));
            } else {
                ivMainPreview.clearColorFilter();
            }
        }
    }

    private void openRatioDialog() {
        RatioBottomSheetDialog dialog = RatioBottomSheetDialog.newInstance(currentRatioMode);
        dialog.setOnRatioSelectedListener(new RatioBottomSheetDialog.OnRatioSelectedListener() {
            @Override
            public void onRatioChanged(RatioBottomSheetDialog.RatioMode mode) {
                applyRatioMode(mode);
            }

            @Override
            public void onDone(RatioBottomSheetDialog.RatioMode selectedMode) {
                currentRatioMode = selectedMode;
                applyRatioMode(selectedMode);
            }

            @Override
            public void onCancel() {
                applyRatioMode(currentRatioMode);
            }
        });
        dialog.show(getSupportFragmentManager(), "RatioBottomSheetDialog");
    }

    private void applyRatioMode(RatioBottomSheetDialog.RatioMode mode) {
        if (ivMainPreview == null) return;

        switch (mode) {
            case ORIGINAL:
                ivMainPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Toast.makeText(this, "Ratio: Original", Toast.LENGTH_SHORT).show();
                break;
            case PORTRAIT:
                ivMainPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Toast.makeText(this, "Ratio: Portrait 4:5", Toast.LENGTH_SHORT).show();
                break;
            case LANDSCAPE:
                ivMainPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Toast.makeText(this, "Ratio: Landscape 16:9", Toast.LENGTH_SHORT).show();
                break;
            case SQUARE:
            default:
                ivMainPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Toast.makeText(this, "Ratio: Square 1:1", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void toggleAspectRatio() {
        if (ivMainPreview == null) return;
        isCropSquare = !isCropSquare;
        if (isCropSquare) {
            ivMainPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Toast.makeText(this, "Square Crop", Toast.LENGTH_SHORT).show();
        } else {
            ivMainPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Toast.makeText(this, "Fit Aspect", Toast.LENGTH_SHORT).show();
        }
    }

    private void openImageAdjustDialog() {
        ImageAdjustBottomSheetDialog dialog = ImageAdjustBottomSheetDialog.newInstance(
                currentBrightness, currentContrast, currentSaturation, isGridVisible
        );
        dialog.setOnImageAdjustListener(new ImageAdjustBottomSheetDialog.OnImageAdjustListener() {
            @Override
            public void onAdjustValuesChanged(float brightness, float contrast, float saturation, boolean showGrid) {
                applyAdjustments(brightness, contrast, saturation, showGrid);
            }

            @Override
            public void onDone(float brightness, float contrast, float saturation, boolean showGrid) {
                applyAdjustments(brightness, contrast, saturation, showGrid);
            }

            @Override
            public void onCancel() {
                applyAdjustments(currentBrightness, currentContrast, currentSaturation, isGridVisible);
            }
        });
        dialog.show(getSupportFragmentManager(), "ImageAdjustBottomSheetDialog");
    }

    private void applyAdjustments(float brightness, float contrast, float saturation, boolean showGrid) {
        currentBrightness = brightness;
        currentContrast = contrast;
        currentSaturation = saturation;
        isGridVisible = showGrid;

        if (gridOverlayView != null) {
            gridOverlayView.setVisibility(showGrid ? View.VISIBLE : View.GONE);
        }

        if (ivMainPreview != null) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(saturation);

            float scale = contrast;
            float translate = (-0.5f * scale + 0.5f) * 255f;
            ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                    scale, 0, 0, 0, translate,
                    0, scale, 0, 0, translate,
                    0, 0, scale, 0, 0, translate,
                    0, 0, 0, 1, 0
            });

            ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{
                    1, 0, 0, 0, brightness,
                    0, 1, 0, 0, brightness,
                    0, 0, 1, 0, brightness,
                    0, 0, 0, 1, 0
            });

            colorMatrix.postConcat(contrastMatrix);
            colorMatrix.postConcat(brightnessMatrix);

            ivMainPreview.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        }
    }

    private void openAddMorePhotosPicker() {
        try {
            if (addMorePhotosLauncher != null) {
                addMorePhotosLauncher.launch(
                        new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()
                );
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open photo picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void openOverlayPhotoPicker() {
        try {
            if (overlayPickerLauncher != null) {
                overlayPickerLauncher.launch(
                        new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()
                );
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open photo picker for overlay", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOverlayPicture(Uri uri) {
        if (ivOverlayPicture != null && uri != null) {
            Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .into(ivOverlayPicture);
        }

        if (containerImageOverlay != null) {
            containerImageOverlay.setVisibility(View.VISIBLE);
            containerImageOverlay.setTranslationX(0);
            containerImageOverlay.setTranslationY(0);
        }
    }

    private void removeOverlayPicture() {
        overlayMediaUri = null;
        if (containerImageOverlay != null) {
            containerImageOverlay.setVisibility(View.GONE);
        }
    }

    private void setupOverlayDragListener() {
        if (containerImageOverlay == null) return;

        containerImageOverlay.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        view.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void proceedToShare() {
        if (mediaUri == null) {
            Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, SharePostActivity.class);
        intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, mediaUri);
        intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_PHOTO);
        if (!multipleUris.isEmpty()) {
            intent.putParcelableArrayListExtra("extra_multiple_uris", new ArrayList<>(multipleUris));
        }
        startActivity(intent);
        finish();
    }
}
