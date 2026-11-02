package com.amstudio.fitbond;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.amstudio.fitbond.svg.SvgTemplateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FoodSnapEditActivity extends AppCompatActivity {

    private static final String TAG = "FoodSnapEditActivity";

    public static final String EXTRA_FOOD_IMAGE_URI = FoodTemplateActivity.EXTRA_FOOD_IMAGE_URI;
    public static final String EXTRA_TEMPLATE_INDEX = "EXTRA_TEMPLATE_INDEX";
    public static final String EXTRA_FOOD_TITLE = FoodTemplateActivity.EXTRA_FOOD_TITLE;
    public static final String EXTRA_FOOD_SUBTITLE = FoodTemplateActivity.EXTRA_FOOD_SUBTITLE;
    public static final String EXTRA_FOOD_GRADE = FoodTemplateActivity.EXTRA_FOOD_GRADE;
    public static final String EXTRA_FOOD_KCAL = FoodTemplateActivity.EXTRA_FOOD_KCAL;
    public static final String EXTRA_FOOD_PROTEIN = FoodTemplateActivity.EXTRA_FOOD_PROTEIN;
    public static final String EXTRA_FOOD_CARBS = FoodTemplateActivity.EXTRA_FOOD_CARBS;
    public static final String EXTRA_FOOD_FAT = FoodTemplateActivity.EXTRA_FOOD_FAT;

    private String foodTitle = "Grilled Chicken Bowl";
    private String foodSubtitle = "FitBond Kitchen · 350g portion";
    private String foodGrade = "A";
    private String kcal = "520 kcal";
    private String protein = "42g";
    private String carbs = "55g";
    private String fat = "12g";
    private @Nullable Uri foodImageUri;
    private @Nullable Bitmap foodImageBitmap;

    private int templateIndex = 0; // 0 = Fitness Stats, 1 = Gym Card

    private ImageView ivFoodBg;
    private MaterialCardView cardSvgTemplateRoot;
    private ImageView ivSvgTemplatePreview;

    private View topEditBar;
    private View bottomEditBar;
    private View layoutDragHint;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_food_snap_edit);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        View rootLayout = findViewById(R.id.root_food_snap_edit);
        topEditBar = findViewById(R.id.top_edit_bar);
        bottomEditBar = findViewById(R.id.bottom_edit_bar);
        layoutDragHint = findViewById(R.id.layout_drag_hint);

        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (topEditBar != null) {
                    topEditBar.setPadding(
                            topEditBar.getPaddingLeft(),
                            systemBars.top + 12,
                            topEditBar.getPaddingRight(),
                            topEditBar.getPaddingBottom()
                    );
                }
                if (bottomEditBar != null) {
                    bottomEditBar.setPadding(
                            bottomEditBar.getPaddingLeft(),
                            bottomEditBar.getPaddingTop(),
                            bottomEditBar.getPaddingRight(),
                            systemBars.bottom + 12
                    );
                }
                return WindowInsetsCompat.CONSUMED;
            });
        }

        parseIntentData();
        initViews();
        loadFoodImageBitmap();
        setupDragAndScaleGestures();
        updateSvgPreview();
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            templateIndex = intent.getIntExtra(EXTRA_TEMPLATE_INDEX, 0);
            if (templateIndex < 0 || templateIndex >= SvgTemplateManager.TEMPLATES.length) {
                templateIndex = 0;
            }
            if (intent.hasExtra(EXTRA_FOOD_TITLE) && intent.getStringExtra(EXTRA_FOOD_TITLE) != null) {
                foodTitle = intent.getStringExtra(EXTRA_FOOD_TITLE);
            }
            if (intent.hasExtra(EXTRA_FOOD_SUBTITLE) && intent.getStringExtra(EXTRA_FOOD_SUBTITLE) != null) {
                foodSubtitle = intent.getStringExtra(EXTRA_FOOD_SUBTITLE);
            }
            if (intent.hasExtra(EXTRA_FOOD_GRADE) && intent.getStringExtra(EXTRA_FOOD_GRADE) != null) {
                foodGrade = intent.getStringExtra(EXTRA_FOOD_GRADE);
            }
            if (intent.hasExtra(EXTRA_FOOD_KCAL) && intent.getStringExtra(EXTRA_FOOD_KCAL) != null) {
                kcal = intent.getStringExtra(EXTRA_FOOD_KCAL);
            }
            if (intent.hasExtra(EXTRA_FOOD_PROTEIN) && intent.getStringExtra(EXTRA_FOOD_PROTEIN) != null) {
                protein = intent.getStringExtra(EXTRA_FOOD_PROTEIN);
            }
            if (intent.hasExtra(EXTRA_FOOD_CARBS) && intent.getStringExtra(EXTRA_FOOD_CARBS) != null) {
                carbs = intent.getStringExtra(EXTRA_FOOD_CARBS);
            }
            if (intent.hasExtra(EXTRA_FOOD_FAT) && intent.getStringExtra(EXTRA_FOOD_FAT) != null) {
                fat = intent.getStringExtra(EXTRA_FOOD_FAT);
            }
            if (intent.hasExtra(EXTRA_FOOD_IMAGE_URI) && intent.getStringExtra(EXTRA_FOOD_IMAGE_URI) != null) {
                foodImageUri = Uri.parse(intent.getStringExtra(EXTRA_FOOD_IMAGE_URI));
            }
        }
    }

    private void initViews() {
        ivFoodBg = findViewById(R.id.iv_food_bg);
        cardSvgTemplateRoot = findViewById(R.id.card_svg_template_root);
        ivSvgTemplatePreview = findViewById(R.id.iv_svg_template_preview);

        if (foodImageUri != null && ivFoodBg != null) {
            ivFoodBg.setImageURI(foodImageUri);
        }

        ImageButton btnBack = findViewById(R.id.btn_back_snap_edit);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }



        MaterialButton btnSendEditedSnap = findViewById(R.id.btn_send_edited_snap);
        if (btnSendEditedSnap != null) {
            btnSendEditedSnap.setOnClickListener(v -> captureAndSendSnap());
        }
    }

    private void openFullscreenImageViewer() {
        String uriStr = foodImageUri != null ? foodImageUri.toString() : null;
        FoodSnapImageViewerDialog dialog = FoodSnapImageViewerDialog.newInstance(
                uriStr, templateIndex, foodTitle, kcal, protein, carbs, fat
        );
        dialog.show(getSupportFragmentManager(), "FoodSnapImageViewerDialog");
    }

    private void loadFoodImageBitmap() {
        if (foodImageUri == null) return;
        executorService.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(foodImageUri)) {
                Bitmap raw = BitmapFactory.decodeStream(is);
                if (raw != null) {
                    foodImageBitmap = scaleBitmapDown(raw, 800);
                    mainHandler.post(this::updateSvgPreview);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading food image bitmap", e);
            }
        });
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



    private void updateSvgPreview() {
        if (ivSvgTemplatePreview == null) return;

        final SvgTemplateManager.TemplateItem item = SvgTemplateManager.TEMPLATES[templateIndex];
        final SvgTemplateManager.FoodData data = new SvgTemplateManager.FoodData(
                foodTitle, kcal, protein, carbs, fat, "", foodImageBitmap
        );

        executorService.execute(() -> {
            Bitmap rendered = SvgTemplateManager.renderTemplateToBitmap(
                    FoodSnapEditActivity.this, item.assetPath, data, 800, 1000
            );
            mainHandler.post(() -> {
                if (ivSvgTemplatePreview != null) {
                    ivSvgTemplatePreview.setImageBitmap(rendered);
                }
            });
        });
    }

    private void setupDragAndScaleGestures() {
        if (cardSvgTemplateRoot != null) {
            attachTouchHandlerToView(cardSvgTemplateRoot);
        }
    }

    private void attachTouchHandlerToView(final View view) {
        // Set pivot to center for smooth scaling around card center
        view.post(() -> {
            if (view != null) {
                view.setPivotX(view.getWidth() / 2f);
                view.setPivotY(view.getHeight() / 2f);
            }
        });

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float scaleFactor = 1.0f;

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                // Clamp scale between 0.35x and 3.0x
                scaleFactor = Math.max(0.35f, Math.min(scaleFactor, 3.0f));

                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);
                return true;
            }
        });

        final GestureDetector doubleTapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float currentScale = view.getScaleX();
                float targetScale = currentScale > 1.1f ? 1.0f : 1.4f;
                view.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(220)
                        .start();
                return true;
            }
        });

        view.setOnTouchListener(new View.OnTouchListener() {
            private float lastTouchX;
            private float lastTouchY;
            private int activePointerId = MotionEvent.INVALID_POINTER_ID;
            private boolean isScaling = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleDetector.onTouchEvent(event);
                doubleTapDetector.onTouchEvent(event);

                if (scaleDetector.isInProgress()) {
                    isScaling = true;
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        isScaling = false;
                        activePointerId = event.getPointerId(0);
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        v.bringToFront();
                        break;
                    }

                    case MotionEvent.ACTION_POINTER_DOWN: {
                        isScaling = true;
                        break;
                    }

                    case MotionEvent.ACTION_MOVE: {
                        if (!isScaling && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                            int pointerIndex = event.findPointerIndex(activePointerId);
                            if (pointerIndex != -1) {
                                float rawX = event.getRawX();
                                float rawY = event.getRawY();

                                float deltaX = rawX - lastTouchX;
                                float deltaY = rawY - lastTouchY;

                                float newX = v.getX() + deltaX;
                                float newY = v.getY() + deltaY;

                                View parent = (View) v.getParent();
                                if (parent != null) {
                                    float halfW = v.getWidth() / 2f;
                                    float halfH = v.getHeight() / 2f;
                                    newX = Math.max(-halfW * 0.8f, Math.min(newX, parent.getWidth() - halfW * 1.2f));
                                    newY = Math.max(20, Math.min(newY, parent.getHeight() - halfH * 1.2f));
                                }

                                v.setX(newX);
                                v.setY(newY);

                                lastTouchX = rawX;
                                lastTouchY = rawY;
                            }
                        }
                        break;
                    }

                    case MotionEvent.ACTION_POINTER_UP: {
                        int pointerIndex = event.getActionIndex();
                        int pointerId = event.getPointerId(pointerIndex);
                        if (pointerId == activePointerId) {
                            int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                            activePointerId = event.getPointerId(newPointerIndex);
                            lastTouchX = event.getRawX();
                            lastTouchY = event.getRawY();
                        }
                        break;
                    }

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                        isScaling = false;
                        break;
                    }
                }
                return true;
            }
        });
    }

    private void resetPositions() {
        if (cardSvgTemplateRoot != null) {
            View parent = (View) cardSvgTemplateRoot.getParent();
            if (parent != null) {
                float centerX = (parent.getWidth() - cardSvgTemplateRoot.getWidth()) / 2f;
                float centerY = (parent.getHeight() - cardSvgTemplateRoot.getHeight()) / 2f;

                cardSvgTemplateRoot.animate()
                        .x(centerX)
                        .y(centerY)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(250)
                        .start();
            }
        }
    }

    private void captureAndSendSnap() {
        View rootLayout = findViewById(R.id.root_food_snap_edit);
        if (rootLayout == null) return;

        if (topEditBar != null) topEditBar.setVisibility(View.GONE);
        if (bottomEditBar != null) bottomEditBar.setVisibility(View.GONE);
        if (layoutDragHint != null) layoutDragHint.setVisibility(View.GONE);

        rootLayout.post(() -> {
            try {
                Bitmap bitmap = Bitmap.createBitmap(rootLayout.getWidth(), rootLayout.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Drawable bgDrawable = rootLayout.getBackground();
                if (bgDrawable != null) {
                    bgDrawable.draw(canvas);
                } else {
                    canvas.drawColor(Color.BLACK);
                }
                rootLayout.draw(canvas);

                File cacheDir = new File(getCacheDir(), "food_snaps");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }

                File snapFile = new File(cacheDir, "food_snap_edited_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(snapFile)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    fos.flush();
                }

                Uri snapUri = Uri.fromFile(snapFile);

                Intent intent = new Intent(FoodSnapEditActivity.this, SelectFriendsActivity.class);
                intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, snapUri.toString());
                intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_PHOTO);
                startActivity(intent);

            } catch (IOException e) {
                Log.e(TAG, "Failed to capture snap", e);
                Toast.makeText(FoodSnapEditActivity.this, "Failed to capture snap", Toast.LENGTH_SHORT).show();
            } finally {
                if (topEditBar != null) topEditBar.setVisibility(View.VISIBLE);
                if (bottomEditBar != null) bottomEditBar.setVisibility(View.VISIBLE);
                if (layoutDragHint != null) layoutDragHint.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
