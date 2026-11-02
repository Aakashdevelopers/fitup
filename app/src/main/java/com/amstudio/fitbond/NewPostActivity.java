package com.amstudio.fitbond;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class NewPostActivity extends AppCompatActivity {

    private static final String TAG = "NewPostActivity";

    private ImageButton btnClose;
    private TextView btnNext;
    private ImageView ivMainPreview;
    private ImageButton btnAspectToggle;
    private View btnMultipleSelect;
    private RecyclerView rvGalleryGrid;
    private View layoutPermissionRequired;
    private MaterialButton btnGrantPermission;

    private GalleryAdapter adapter;
    private final List<Uri> photoUris = new ArrayList<>();
    private final List<Uri> selectedPhotoUris = new ArrayList<>();
    private Uri selectedPhotoUri;
    private boolean isMultipleSelectMode = false;

    private ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_new_post);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        View rootLayout = findViewById(R.id.root_new_post);
        View topBar = findViewById(R.id.top_nav_bar);
        if (rootLayout != null && topBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int extra12Dp = (int) (12 * getResources().getDisplayMetrics().density);
                int topPadding = systemBars.top + extra12Dp;
                topBar.setPadding(
                        topBar.getPaddingLeft(),
                        topPadding,
                        topBar.getPaddingRight(),
                        topBar.getPaddingBottom()
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        initViews();
        setupRecyclerView();
        initPermissionLauncher();
        checkAndLoadMedia();
    }

    private void initViews() {
        btnClose = findViewById(R.id.btn_close_new_post);
        btnNext = findViewById(R.id.btn_next_new_post);
        ivMainPreview = findViewById(R.id.iv_main_preview);
        btnAspectToggle = findViewById(R.id.btn_aspect_toggle);
        btnMultipleSelect = findViewById(R.id.btn_multiple_select);
        rvGalleryGrid = findViewById(R.id.rv_gallery_grid);
        layoutPermissionRequired = findViewById(R.id.layout_permission_required);
        btnGrantPermission = findViewById(R.id.btn_grant_permission);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> proceedToNextStep());
        }

        if (btnAspectToggle != null) {
            btnAspectToggle.setOnClickListener(v -> toggleAspectRatio());
        }

        if (btnMultipleSelect != null) {
            btnMultipleSelect.setOnClickListener(v -> toggleMultipleSelectMode());
        }

        if (btnGrantPermission != null) {
            btnGrantPermission.setOnClickListener(v -> requestGalleryPermissions());
        }
    }

    private boolean isCropSquare = true;

    private void toggleAspectRatio() {
        if (ivMainPreview == null) return;
        isCropSquare = !isCropSquare;
        if (isCropSquare) {
            ivMainPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Toast.makeText(this, "Square Crop", Toast.LENGTH_SHORT).show();
        } else {
            ivMainPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Toast.makeText(this, "Fit Aspect Ratio", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleMultipleSelectMode() {
        isMultipleSelectMode = !isMultipleSelectMode;
        if (isMultipleSelectMode) {
            if (btnMultipleSelect != null) btnMultipleSelect.setAlpha(1.0f);
            if (selectedPhotoUri != null && !selectedPhotoUris.contains(selectedPhotoUri)) {
                selectedPhotoUris.add(selectedPhotoUri);
            }
            Toast.makeText(this, "Multiple selection enabled", Toast.LENGTH_SHORT).show();
        } else {
            if (btnMultipleSelect != null) btnMultipleSelect.setAlpha(0.6f);
            selectedPhotoUris.clear();
            if (selectedPhotoUri != null) {
                selectedPhotoUris.add(selectedPhotoUri);
            }
            Toast.makeText(this, "Single selection enabled", Toast.LENGTH_SHORT).show();
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void setupRecyclerView() {
        if (rvGalleryGrid == null) return;

        adapter = new GalleryAdapter(photoUris, selectedPhotoUris, new OnMediaClickListener() {
            @Override
            public void onCameraClick() {
                openCamera();
            }

            @Override
            public void onPhotoClick(Uri uri) {
                onPhotoThumbnailClicked(uri);
            }
        });

        GridLayoutManager glm = new GridLayoutManager(this, 4);
        rvGalleryGrid.setLayoutManager(glm);
        rvGalleryGrid.setAdapter(adapter);
    }

    private void onPhotoThumbnailClicked(Uri uri) {
        if (isMultipleSelectMode) {
            if (selectedPhotoUris.contains(uri)) {
                selectedPhotoUris.remove(uri);
                if (selectedPhotoUris.isEmpty() && !photoUris.isEmpty()) {
                    selectedPhotoUri = photoUris.get(0);
                } else if (!selectedPhotoUris.isEmpty()) {
                    selectedPhotoUri = selectedPhotoUris.get(selectedPhotoUris.size() - 1);
                }
            } else {
                selectedPhotoUris.add(uri);
                selectedPhotoUri = uri;
            }
        } else {
            selectedPhotoUris.clear();
            selectedPhotoUris.add(uri);
            selectedPhotoUri = uri;
        }

        updateSelectedPhoto(selectedPhotoUri);

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void initPermissionLauncher() {
        requestMultiplePermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = false;
                    for (Boolean isGranted : result.values()) {
                        if (Boolean.TRUE.equals(isGranted)) {
                            granted = true;
                            break;
                        }
                    }
                    if (granted) {
                        showGalleryView();
                        loadDevicePhotos();
                    } else {
                        showPermissionRequiredView();
                    }
                }
        );
    }

    private void checkAndLoadMedia() {
        if (hasGalleryPermissions()) {
            showGalleryView();
            loadDevicePhotos();
        } else {
            showPermissionRequiredView();
            requestGalleryPermissions();
        }
    }

    private boolean hasGalleryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestGalleryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestMultiplePermissionsLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestMultiplePermissionsLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        } else {
            requestMultiplePermissionsLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void showGalleryView() {
        if (layoutPermissionRequired != null) layoutPermissionRequired.setVisibility(View.GONE);
        if (rvGalleryGrid != null) rvGalleryGrid.setVisibility(View.VISIBLE);
    }

    private void showPermissionRequiredView() {
        if (layoutPermissionRequired != null) layoutPermissionRequired.setVisibility(View.VISIBLE);
        if (rvGalleryGrid != null) rvGalleryGrid.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasGalleryPermissions()) {
            showGalleryView();
            if (photoUris.isEmpty()) {
                loadDevicePhotos();
            }
        }
    }

    private void loadDevicePhotos() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Uri> loadedUris = new ArrayList<>();
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

            String[] projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED
            };

            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = getContentResolver().query(collection, projection, null, null, sortOrder)) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                    if (idColumn != -1) {
                        Set<Long> seenIds = new HashSet<>();
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            if (seenIds.add(id)) {
                                Uri contentUri = ContentUris.withAppendedId(collection, id);
                                loadedUris.add(contentUri);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load MediaStore images for " + collection, e);
            }

            Log.d(TAG, "Loaded device photos total count: " + loadedUris.size());

            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;

                photoUris.clear();
                photoUris.addAll(loadedUris);

                if (!photoUris.isEmpty()) {
                    updateSelectedPhoto(photoUris.get(0));
                } else {
                    addSampleFallbackPhotos();
                }

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        });
    }

    private void addSampleFallbackPhotos() {
        try {
            Uri sampleUri1 = Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.bg_sample_gym_preview);
            Uri sampleUri2 = Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.ic_avatar_placeholder);
            photoUris.add(sampleUri1);
            photoUris.add(sampleUri2);
            updateSelectedPhoto(sampleUri1);
        } catch (Exception ignored) {
        }
    }

    private void updateSelectedPhoto(Uri uri) {
        selectedPhotoUri = uri;
        if (uri != null && !selectedPhotoUris.contains(uri) && !isMultipleSelectMode) {
            selectedPhotoUris.clear();
            selectedPhotoUris.add(uri);
        }
        if (ivMainPreview != null && uri != null) {
            Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .into(ivMainPreview);
        }
    }

    private void openCamera() {
        if (getCallingActivity() != null || isTaskRoot()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("ACTION_NAVIGATE_TAG", MainActivity.TAG_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    private void proceedToNextStep() {
        if (selectedPhotoUris.isEmpty() && selectedPhotoUri == null) {
            Toast.makeText(this, "Please select a photo to post", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri targetUri = !selectedPhotoUris.isEmpty() ? selectedPhotoUris.get(0) : selectedPhotoUri;

        Intent intent = new Intent(this, EditPostActivity.class);
        intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, targetUri);
        intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_PHOTO);
        if (selectedPhotoUris.size() > 1) {
            intent.putParcelableArrayListExtra("extra_multiple_uris", new ArrayList<>(selectedPhotoUris));
        }
        startActivity(intent);
        finish();
    }

    interface OnMediaClickListener {
        void onCameraClick();
        void onPhotoClick(Uri uri);
    }

    // RecyclerView Adapter
    private class GalleryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_CAMERA = 0;
        private static final int TYPE_PHOTO = 1;

        private final List<Uri> list;
        private final List<Uri> selectedList;
        private final OnMediaClickListener listener;

        GalleryAdapter(List<Uri> list, List<Uri> selectedList, OnMediaClickListener listener) {
            this.list = list;
            this.selectedList = selectedList;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_CAMERA : TYPE_PHOTO;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int columnWidth = parent.getWidth() / 4;
            if (viewType == TYPE_CAMERA) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery_camera, parent, false);
                if (columnWidth > 0) {
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    if (lp != null) {
                        lp.height = columnWidth;
                        view.setLayoutParams(lp);
                    }
                }
                return new CameraViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery_photo, parent, false);
                if (columnWidth > 0) {
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    if (lp != null) {
                        lp.height = columnWidth;
                        view.setLayoutParams(lp);
                    }
                }
                return new PhotoViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_CAMERA) {
                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onCameraClick();
                });
            } else {
                Uri uri = list.get(position - 1);
                PhotoViewHolder photoHolder = (PhotoViewHolder) holder;
                photoHolder.bind(uri, selectedList.indexOf(uri), isMultipleSelectMode, v -> {
                    if (listener != null) {
                        listener.onPhotoClick(uri);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return list.size() + 1;
        }

        class CameraViewHolder extends RecyclerView.ViewHolder {
            CameraViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView ivThumbnail;
            View viewOverlay;
            TextView tvBadge;

            PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                ivThumbnail = itemView.findViewById(R.id.iv_gallery_thumbnail);
                viewOverlay = itemView.findViewById(R.id.view_selected_overlay);
                tvBadge = itemView.findViewById(R.id.tv_selection_badge);
            }

            void bind(Uri uri, int selectedIndex, boolean multiSelectMode, View.OnClickListener clickListener) {
                boolean isSelected = selectedIndex != -1;

                if (ivThumbnail != null) {
                    Glide.with(itemView.getContext())
                            .load(uri)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .error(R.drawable.ic_avatar_placeholder)
                            .into(ivThumbnail);
                }

                if (viewOverlay != null) {
                    viewOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                }

                if (tvBadge != null) {
                    if (multiSelectMode) {
                        tvBadge.setVisibility(View.VISIBLE);
                        if (isSelected) {
                            tvBadge.setText(String.valueOf(selectedIndex + 1));
                            tvBadge.setBackgroundResource(R.drawable.bg_avatar_edit_badge);
                            tvBadge.setTextColor(android.graphics.Color.WHITE);
                        } else {
                            tvBadge.setText("");
                            tvBadge.setBackgroundResource(R.drawable.bg_control_circle);
                        }
                    } else {
                        tvBadge.setVisibility(View.GONE);
                    }
                }

                itemView.setOnClickListener(clickListener);
            }
        }
    }
}
