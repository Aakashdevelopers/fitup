package com.amstudio.fitbond;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitbond.model.GalleryItem;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Interactive Gallery Image Preview Activity with Top Header, Curved CoverFlow Thumbnail Strip,
 * Real-time Scroll Image Updating, Delete Action, and Left/Right Swipe Navigation.
 */
public class GalleryPreviewActivity extends AppCompatActivity {

    private static final String TAG = "GalleryPreviewActivity";

    public static final String EXTRA_ITEMS = "extra_items";
    public static final String EXTRA_IMAGE_URIS = "extra_image_uris";
    public static final String EXTRA_IMAGE_DATES = "extra_image_dates";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_START_INDEX = "extra_start_index";

    private ImageButton btnClose;
    private ImageButton btnDelete;
    private TextView tvTitle;
    private ImageView ivMainPreview;
    private MaterialCardView cardMainPreview;
    private TextView tvDate;
    private RecyclerView rvThumbnails;

    private final List<GalleryItem> galleryItems = new ArrayList<>();
    private GalleryThumbnailAdapter thumbnailAdapter;
    private LinearLayoutManager layoutManager;
    private LinearSnapHelper snapHelper;

    private int selectedIndex = 0;
    private String pageTitle = "Gallery";

    private GestureDetector gestureDetector;

    private final ActivityResultLauncher<String[]> mediaPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                boolean isGranted = false;
                for (Boolean granted : permissions.values()) {
                    if (Boolean.TRUE.equals(granted)) {
                        isGranted = true;
                        break;
                    }
                }
                final boolean finalGranted = isGranted;
                Executors.newSingleThreadExecutor().execute(() -> {
                    List<GalleryItem> loaded = finalGranted ? queryDevicePhotos() : new ArrayList<>();
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        galleryItems.clear();
                        if (!loaded.isEmpty()) {
                            galleryItems.addAll(loaded);
                        } else {
                            loadSamplePhotos();
                        }
                        selectedIndex = 0;
                        if (thumbnailAdapter != null) {
                            thumbnailAdapter.notifyDataSetChanged();
                        }
                        updateMainPreviewContent(selectedIndex, true);
                    });
                });
            }
    );

    public static void open(Context context, ArrayList<GalleryItem> items, int startIndex, String title) {
        Intent intent = new Intent(context, GalleryPreviewActivity.class);
        intent.putParcelableArrayListExtra(EXTRA_ITEMS, items);
        intent.putExtra(EXTRA_START_INDEX, startIndex);
        if (title != null) intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    public static void open(Context context, ArrayList<String> uris, ArrayList<String> dates, String title) {
        Intent intent = new Intent(context, GalleryPreviewActivity.class);
        intent.putStringArrayListExtra(EXTRA_IMAGE_URIS, uris);
        if (dates != null) intent.putStringArrayListExtra(EXTRA_IMAGE_DATES, dates);
        if (title != null) intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    public static void openWithSampleData(Context context, String title) {
        Intent intent = new Intent(context, GalleryPreviewActivity.class);
        if (title != null) intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindowFlags();
        setContentView(R.layout.activity_gallery_preview);

        initViews();
        parseIntentData();
        setupGestureDetector();
        setupRecyclerView();
        setupListeners();
        if (!galleryItems.isEmpty()) {
            updateMainPreviewContent(selectedIndex, true);
        }
    }

    private void setupWindowFlags() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
    }

    private void initViews() {
        btnClose = findViewById(R.id.btn_close);
        btnDelete = findViewById(R.id.btn_delete);
        tvTitle = findViewById(R.id.tv_title);
        ivMainPreview = findViewById(R.id.iv_main_preview);
        cardMainPreview = findViewById(R.id.card_main_preview);
        tvDate = findViewById(R.id.tv_date);
        rvThumbnails = findViewById(R.id.rv_thumbnails);
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_TITLE)) {
                String titleExtra = intent.getStringExtra(EXTRA_TITLE);
                if (titleExtra != null && !titleExtra.trim().isEmpty()) {
                    pageTitle = titleExtra;
                }
            }

            selectedIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);

            if (intent.hasExtra(EXTRA_ITEMS)) {
                ArrayList<GalleryItem> parcelables = intent.getParcelableArrayListExtra(EXTRA_ITEMS);
                if (parcelables != null && !parcelables.isEmpty()) {
                    galleryItems.addAll(parcelables);
                }
            } else if (intent.hasExtra(EXTRA_IMAGE_URIS)) {
                ArrayList<String> uris = intent.getStringArrayListExtra(EXTRA_IMAGE_URIS);
                ArrayList<String> dates = intent.getStringArrayListExtra(EXTRA_IMAGE_DATES);
                if (uris != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
                    String defaultDate = sdf.format(new Date());
                    for (int i = 0; i < uris.size(); i++) {
                        String uri = uris.get(i);
                        String date = (dates != null && i < dates.size() && dates.get(i) != null) ? dates.get(i) : defaultDate;
                        galleryItems.add(new GalleryItem(uri, date, pageTitle));
                    }
                }
            }
        }

        tvTitle.setText(pageTitle);

        if (galleryItems.isEmpty()) {
            loadDeviceOrSamplePhotos();
        }

        if (selectedIndex < 0 || selectedIndex >= galleryItems.size()) {
            selectedIndex = 0;
        }
    }

    private void loadDeviceOrSamplePhotos() {
        if (hasGalleryPermission()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                List<GalleryItem> loaded = queryDevicePhotos();
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    galleryItems.clear();
                    if (!loaded.isEmpty()) {
                        galleryItems.addAll(loaded);
                    } else {
                        loadSamplePhotos();
                    }
                    if (thumbnailAdapter != null) thumbnailAdapter.notifyDataSetChanged();
                    updateMainPreviewContent(selectedIndex, true);
                });
            });
        } else {
            requestGalleryPermission();
        }
    }

    private boolean hasGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasImages = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                boolean hasUserSelected = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
                return hasImages || hasUserSelected;
            }
            return hasImages;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES
            });
        } else {
            mediaPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void loadSamplePhotos() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();

        String todayStr = dateFormat.format(calendar.getTime());
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        String yesterdayStr = dateFormat.format(calendar.getTime());
        calendar.add(Calendar.DAY_OF_MONTH, -2);
        String prevStr = dateFormat.format(calendar.getTime());

        galleryItems.add(new GalleryItem("android.resource://" + getPackageName() + "/" + R.drawable.bg_sample_gym_preview, "18 May, 2025", pageTitle));
        galleryItems.add(new GalleryItem("android.resource://" + getPackageName() + "/" + R.drawable.bg_auth_gradient, todayStr, pageTitle));
        galleryItems.add(new GalleryItem("android.resource://" + getPackageName() + "/" + R.drawable.bg_splash_gradient, yesterdayStr, pageTitle));
        galleryItems.add(new GalleryItem("android.resource://" + getPackageName() + "/" + R.drawable.bg_welcome_gradient, prevStr, pageTitle));
        galleryItems.add(new GalleryItem("android.resource://" + getPackageName() + "/" + R.drawable.bg_profile_gradient, "12 May, 2025", pageTitle));
    }

    private List<GalleryItem> queryDevicePhotos() {
        List<GalleryItem> items = new ArrayList<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
        };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                int dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);

                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());

                while (cursor.moveToNext()) {
                    if (idColumn != -1) {
                        long id = cursor.getLong(idColumn);
                        long dateAddedSec = (dateColumn != -1) ? cursor.getLong(dateColumn) : (System.currentTimeMillis() / 1000L);

                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        String dateStr = dateFormat.format(new Date(dateAddedSec * 1000L));

                        items.add(new GalleryItem(contentUri.toString(), dateStr, pageTitle));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query device photos", e);
        }
        return items;
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            selectNextImage();
                        } else {
                            selectPreviousImage();
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        cardMainPreview.setOnTouchListener((v, event) -> {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && !handled) {
                v.performClick();
            }
            return handled || v.onTouchEvent(event);
        });
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvThumbnails.setLayoutManager(layoutManager);

        rvThumbnails.setClipChildren(false);
        rvThumbnails.setClipToPadding(false);
        if (rvThumbnails.getParent() instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) rvThumbnails.getParent();
            parentGroup.setClipChildren(false);
            parentGroup.setClipToPadding(false);
        }

        snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvThumbnails);

        thumbnailAdapter = new GalleryThumbnailAdapter(galleryItems, selectedIndex, (position, item) -> {
            smoothScrollToCenter(position);
        });
        rvThumbnails.setAdapter(thumbnailAdapter);

        // Dynamically calculate and set side padding so items can center perfectly
        rvThumbnails.post(() -> {
            int rvWidth = rvThumbnails.getWidth();
            int itemWidth = (int) (60 * getResources().getDisplayMetrics().density);
            int sidePadding = (rvWidth - itemWidth) / 2;
            if (sidePadding > 0) {
                rvThumbnails.setPadding(sidePadding, rvThumbnails.getPaddingTop(), sidePadding, rvThumbnails.getPaddingBottom());
                rvThumbnails.setClipToPadding(false);
            }
            smoothScrollToCenter(selectedIndex);
            updateCenterItemAndTransform();
        });

        rvThumbnails.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int snapPosition = layoutManager.getPosition(centerView);
                        if (snapPosition != RecyclerView.NO_POSITION && snapPosition != selectedIndex) {
                            selectedIndex = snapPosition;
                            updateMainPreviewContent(selectedIndex, false);
                        }
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateCenterItemAndTransform();
            }
        });
    }

    private void updateCenterItemAndTransform() {
        int rvWidth = rvThumbnails.getWidth();
        if (rvWidth <= 0) return;

        float center = rvWidth / 2f;
        float maxDistance = rvWidth / 2f;
        float circleRadius = rvWidth * 0.60f; // Circle arc radius for curved layout

        int closestPos = -1;
        float minDistance = Float.MAX_VALUE;

        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            View child = layoutManager.getChildAt(i);
            if (child == null) continue;

            float childCenter = (child.getLeft() + child.getRight()) / 2f;
            float deltaX = childCenter - center;
            float distance = Math.abs(deltaX);

            if (distance < minDistance) {
                minDistance = distance;
                closestPos = layoutManager.getPosition(child);
            }

            // Normalized distance ratio [0.0 at center, 1.0 at edge]
            float normDist = Math.min(1.0f, distance / maxDistance);

            // Circular Arc Equation (Circle Wheel animation)
            float clampedDelta = Math.max(-circleRadius, Math.min(circleRadius, deltaX));
            double angleRad = Math.asin(clampedDelta / circleRadius);

            // Vertical position along circle arc (Apex at center, dips downward on sides)
            float translateY = (float) (circleRadius * (1.0 - Math.cos(angleRad)));

            // Tangential rotation along circle path (degrees)
            float rotationZ = (float) Math.toDegrees(angleRad) * 0.70f;

            // Subtle 3D Y-axis perspective rotation for depth effect
            float rotationY = -(deltaX / maxDistance) * 20f;

            // Scale and opacity tapering towards edges
            float scale = 1.0f - (normDist * 0.22f); // 1.0 at center -> 0.78 at edges
            float alpha = 1.0f - (normDist * 0.45f); // 1.0 at center -> 0.55 at edges

            // Elevation Z-translation to bring center item to foreground
            float translationZ = (1.0f - normDist) * 12f;

            // Set pivot to item center
            child.setPivotX(child.getWidth() / 2f);
            child.setPivotY(child.getHeight() / 2f);

            // Apply transformations
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
            child.setTranslationY(translateY);
            child.setRotation(rotationZ);
            child.setRotationY(rotationY);
            child.setTranslationZ(translationZ);
        }

        // Live update main preview image as user scrolls left/right!
        if (closestPos != -1 && closestPos != selectedIndex && closestPos < galleryItems.size()) {
            selectedIndex = closestPos;
            updateMainPreviewContent(selectedIndex, false);
        }
    }

    private void smoothScrollToCenter(int position) {
        if (position < 0 || position >= galleryItems.size()) return;

        RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(this) {
            @Override
            protected int getHorizontalSnapPreference() {
                return SNAP_TO_START;
            }

            @Override
            public int calculateDxToMakeVisible(View view, int snapPreference) {
                if (layoutManager == null || !layoutManager.canScrollHorizontally()) {
                    return 0;
                }
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                int left = layoutManager.getDecoratedLeft(view) - params.leftMargin;
                int right = layoutManager.getDecoratedRight(view) + params.rightMargin;
                int start = layoutManager.getPaddingLeft();
                int end = layoutManager.getWidth() - layoutManager.getPaddingRight();
                int viewCenter = left + (right - left) / 2;
                int containerCenter = start + (end - start) / 2;
                return containerCenter - viewCenter;
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return 150f / displayMetrics.densityDpi;
            }
        };

        smoothScroller.setTargetPosition(position);
        layoutManager.startSmoothScroll(smoothScroller);
        selectedIndex = position;
        updateMainPreviewContent(selectedIndex, false);
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> confirmDeleteCurrentImage());
    }

    private void updateMainPreviewContent(int index, boolean scrollStrip) {
        if (galleryItems.isEmpty()) {
            Toast.makeText(this, "No images available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (index < 0 || index >= galleryItems.size()) return;

        GalleryItem currentItem = galleryItems.get(index);

        // Load image into main rounded preview card
        Glide.with(this)
                .load(currentItem.getUri())
                .placeholder(R.drawable.bg_sample_gym_preview)
                .error(R.drawable.bg_sample_gym_preview)
                .into(ivMainPreview);

        // Update date text
        tvDate.setText(currentItem.getDate());

        // Update title text if customized
        if (currentItem.getTitle() != null && !currentItem.getTitle().isEmpty()) {
            tvTitle.setText(currentItem.getTitle());
        }

        // Sync adapter selection state
        if (thumbnailAdapter != null) {
            thumbnailAdapter.setSelectedIndex(index);
            rvThumbnails.post(this::updateCenterItemAndTransform);
        }

        if (scrollStrip) {
            smoothScrollToCenter(index);
        }
    }

    private void selectNextImage() {
        if (selectedIndex < galleryItems.size() - 1) {
            smoothScrollToCenter(selectedIndex + 1);
        }
    }

    private void selectPreviousImage() {
        if (selectedIndex > 0) {
            smoothScrollToCenter(selectedIndex - 1);
        }
    }

    private void confirmDeleteCurrentImage() {
        if (galleryItems.isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle("Delete Photo")
                .setMessage("Are you sure you want to remove this photo from preview?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCurrentImage())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCurrentImage() {
        if (selectedIndex < 0 || selectedIndex >= galleryItems.size()) return;

        galleryItems.remove(selectedIndex);
        thumbnailAdapter.notifyItemRemoved(selectedIndex);

        if (galleryItems.isEmpty()) {
            Toast.makeText(this, "Gallery preview cleared", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if (selectedIndex >= galleryItems.size()) {
                selectedIndex = galleryItems.size() - 1;
            }
            thumbnailAdapter.notifyDataSetChanged();
            updateMainPreviewContent(selectedIndex, true);
        }
    }
}
