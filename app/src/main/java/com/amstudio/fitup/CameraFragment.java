package com.amstudio.fitup;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Premium Snapchat-inspired Camera Fragment for Gym Social App.
 */
public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";
    private static final long MAX_RECORDING_MS = 15000; // 15 seconds max

    public static final int MODE_PHOTO = 0;
    public static final int MODE_VIDEO = 1;
    public static final int MODE_FOOD_ANALYZE = 2;
    private int currentMode = MODE_PHOTO;

    // Views
    private PreviewView previewView;
    private GridOverlayView gridOverlay;
    private FocusRingView focusRing;
    private View layoutPermission;
    private View topControlBar;
    private ImageButton btnFlash;
    private ImageButton btnCameraSwitch;
    private TextView tvZoomIndicator;
    private View layoutRecordingTimer;
    private TextView tvRecordingTimer;
    private View vRecordingDot;
    private View vCaptureFlash;
    private View bottomCaptureContainer;
    private View modeSelectorContainer;
    private TextView btnModePhoto;
    private TextView btnModeVideo;
    private TextView btnModeFood;
    private View layoutFoodScanOverlay;
    private FrameLayout btnGallery;
    private ImageView ivGalleryIcon;
    private ImageView ivGalleryThumbnail;
    private FrameLayout btnShutter;
    private View vShutterOuter;
    private View vShutterInner;
    private ImageButton btnGrid;

    // Media Preview Views
    private View layoutMediaPreview;
    private ImageView ivPreviewImage;
    private VideoView vvPreviewVideo;
    private ImageButton btnPreviewClose;
    private View btnPreviewSave;

    // CameraX State
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;

    // Configuration State
    private boolean isFrontCamera = false;
    private boolean isFlashOn = false;
    private boolean isPhotoMode = true;
    private boolean isGridVisible = false;
    private boolean isCapturing = false;
    private long recordingStartTime = 0;

    // Handlers & Launchers
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable hideZoomRunnable;

    private MediaRepository mediaRepository;

    // Permission Launchers
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;

    public CameraFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mediaRepository = new LocalMediaRepository(requireContext());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean cameraGranted = Boolean.TRUE.equals(permissions.get(Manifest.permission.CAMERA));
                    if (cameraGranted) {
                        layoutPermission.setVisibility(View.GONE);
                        topControlBar.setVisibility(View.VISIBLE);
                        bottomCaptureContainer.setVisibility(View.VISIBLE);
                        startCamera();
                    } else {
                        layoutPermission.setVisibility(View.VISIBLE);
                        topControlBar.setVisibility(View.GONE);
                        bottomCaptureContainer.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Camera permission required for preview", Toast.LENGTH_SHORT).show();
                    }
                });

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        ArrayList<String> uris = new ArrayList<>();
                        uris.add(uri.toString());
                        GalleryPreviewActivity.open(requireContext(), uris, null, "Read at least 10 pages");
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupListeners(view);
        setupGestures();

        checkAndRequestPermissions();
    }

    private void bindViews(@NonNull View view) {
        previewView = view.findViewById(R.id.preview_view);
        gridOverlay = view.findViewById(R.id.grid_overlay);
        focusRing = view.findViewById(R.id.focus_ring);
        layoutPermission = view.findViewById(R.id.layout_permission);
        topControlBar = view.findViewById(R.id.top_control_bar);

        if (topControlBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topControlBar, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                int topPadding = statusBarHeight > 0 ? statusBarHeight + (int) (8 * getResources().getDisplayMetrics().density) : (int) (36 * getResources().getDisplayMetrics().density);
                v.setPadding(v.getPaddingLeft(), topPadding, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        btnFlash = view.findViewById(R.id.btn_flash);
        btnCameraSwitch = view.findViewById(R.id.btn_camera_switch);
        tvZoomIndicator = view.findViewById(R.id.tv_zoom_indicator);
        layoutRecordingTimer = view.findViewById(R.id.layout_recording_timer);
        tvRecordingTimer = view.findViewById(R.id.tv_recording_timer);
        vRecordingDot = view.findViewById(R.id.v_recording_dot);
        vCaptureFlash = view.findViewById(R.id.v_capture_flash);
        bottomCaptureContainer = view.findViewById(R.id.bottom_capture_container);
        modeSelectorContainer = view.findViewById(R.id.mode_selector_container);
        btnModePhoto = view.findViewById(R.id.btn_mode_photo);
        btnModeVideo = view.findViewById(R.id.btn_mode_video);
        btnModeFood = view.findViewById(R.id.btn_mode_food);
        layoutFoodScanOverlay = view.findViewById(R.id.layout_food_scan_overlay);
        btnGallery = view.findViewById(R.id.btn_gallery);
        ivGalleryIcon = view.findViewById(R.id.iv_gallery_icon);
        ivGalleryThumbnail = view.findViewById(R.id.iv_gallery_thumbnail);
        btnShutter = view.findViewById(R.id.btn_shutter);
        vShutterOuter = view.findViewById(R.id.v_shutter_outer);
        vShutterInner = view.findViewById(R.id.v_shutter_inner);
        btnGrid = view.findViewById(R.id.btn_grid);

        layoutMediaPreview = view.findViewById(R.id.layout_media_preview);
        View previewTopBar = view.findViewById(R.id.preview_top_bar);
        if (previewTopBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(previewTopBar, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                int topPadding = statusBarHeight > 0 ? statusBarHeight + (int) (8 * getResources().getDisplayMetrics().density) : (int) (36 * getResources().getDisplayMetrics().density);
                v.setPadding(v.getPaddingLeft(), topPadding, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
        ivPreviewImage = view.findViewById(R.id.iv_preview_image);
        vvPreviewVideo = view.findViewById(R.id.vv_preview_video);
        btnPreviewClose = view.findViewById(R.id.btn_preview_close);
        btnPreviewSave = view.findViewById(R.id.btn_preview_save);
    }

    private void setupListeners(@NonNull View view) {
        view.findViewById(R.id.btn_grant_permission).setOnClickListener(v -> checkAndRequestPermissions());

        btnFlash.setOnClickListener(v -> toggleFlash());

        btnCameraSwitch.setOnClickListener(v -> {
            isFrontCamera = !isFrontCamera;
            btnCameraSwitch.animate().rotationBy(180f).setDuration(300).start();
            startCamera();
        });

        btnGrid.setOnClickListener(v -> {
            isGridVisible = !isGridVisible;
            gridOverlay.setVisibility(isGridVisible ? View.VISIBLE : View.GONE);
            btnGrid.setImageResource(isGridVisible ? R.drawable.ic_grid_on : R.drawable.ic_grid_off);
        });

        btnModePhoto.setOnClickListener(v -> setCaptureMode(MODE_PHOTO));
        btnModeVideo.setOnClickListener(v -> setCaptureMode(MODE_VIDEO));
        if (btnModeFood != null) {
            btnModeFood.setOnClickListener(v -> setCaptureMode(MODE_FOOD_ANALYZE));
        }

        btnShutter.setOnClickListener(v -> {
            if (currentMode == MODE_PHOTO) {
                takePhoto();
            } else if (currentMode == MODE_VIDEO) {
                toggleVideoRecording();
            } else if (currentMode == MODE_FOOD_ANALYZE) {
                takeFoodPhotoAndAnalyze();
            }
        });

        btnGallery.setOnClickListener(v -> {
            GalleryPreviewActivity.openWithSampleData(requireContext(), "Read at least 10 pages");
        });

        btnGallery.setOnLongClickListener(v -> {
            pickMediaLauncher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                            .build()
            );
            return true;
        });

        btnPreviewClose.setOnClickListener(v -> {
            if (vvPreviewVideo.isPlaying()) {
                vvPreviewVideo.stopPlayback();
            }
            layoutMediaPreview.setVisibility(View.GONE);
        });

        btnPreviewSave.setOnClickListener(v -> {
            if (vvPreviewVideo.isPlaying()) {
                vvPreviewVideo.stopPlayback();
            }
            layoutMediaPreview.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Media selected", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupGestures() {
        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(requireContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        if (camera == null) return false;
                        float currentZoomRatio = (camera.getCameraInfo().getZoomState().getValue() != null) ?
                                camera.getCameraInfo().getZoomState().getValue().getZoomRatio() : 1.0f;
                        float delta = detector.getScaleFactor();
                        float targetZoomRatio = currentZoomRatio * delta;

                        camera.getCameraControl().setZoomRatio(targetZoomRatio);

                        showZoomIndicator(targetZoomRatio);
                        return true;
                    }
                });

        GestureDetector tapDetector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                        if (camera == null) return false;
                        MeteringPointFactory factory = previewView.getMeteringPointFactory();
                        MeteringPoint point = factory.createPoint(e.getX(), e.getY());
                        FocusMeteringAction action = new FocusMeteringAction.Builder(point,
                                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build();

                        camera.getCameraControl().startFocusAndMetering(action);
                        focusRing.showFocusAt(e.getX(), e.getY());
                        return true;
                    }
                });

        previewView.setOnTouchListener((v, event) -> {
            v.performClick();
            scaleDetector.onTouchEvent(event);
            if (!scaleDetector.isInProgress()) {
                tapDetector.onTouchEvent(event);
            }
            return true;
        });
    }

    private void showZoomIndicator(float zoomRatio) {
        tvZoomIndicator.setText(String.format(Locale.US, "%.1fx", zoomRatio));
        tvZoomIndicator.setVisibility(View.VISIBLE);

        if (hideZoomRunnable != null) {
            mainHandler.removeCallbacks(hideZoomRunnable);
        }
        hideZoomRunnable = () -> tvZoomIndicator.setVisibility(View.GONE);
        mainHandler.postDelayed(hideZoomRunnable, 1500);
    }

    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA);
        }

        if (requiredPermissions.isEmpty()) {
            layoutPermission.setVisibility(View.GONE);
            topControlBar.setVisibility(View.VISIBLE);
            bottomCaptureContainer.setVisibility(View.VISIBLE);
            startCamera();
        } else {
            permissionLauncher.launch(requiredPermissions.toArray(new String[0]));
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to initialize ProcessCameraProvider", e);
                Toast.makeText(getContext(), "Camera initialization failed", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = isFrontCamera ?
                CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(isFlashOn && !isFrontCamera ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF)
                .build();

        Recorder recorder = new Recorder.Builder().build();
        videoCapture = VideoCapture.withOutput(recorder);

        try {
            camera = cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
            );

            updateFlashAvailability();

        } catch (Exception e) {
            Log.e(TAG, "Binding camera use cases failed", e);
            Toast.makeText(getContext(), "Unable to start camera preview", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFlashAvailability() {
        if (camera == null) return;

        boolean hasFlash = camera.getCameraInfo().hasFlashUnit();
        if (isFrontCamera || !hasFlash) {
            btnFlash.setAlpha(0.4f);
            btnFlash.setImageResource(R.drawable.ic_flash_off);
            isFlashOn = false;
        } else {
            btnFlash.setAlpha(1.0f);
            btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        }
    }

    private void toggleFlash() {
        if (camera == null) return;
        boolean hasFlash = camera.getCameraInfo().hasFlashUnit();
        if (isFrontCamera || !hasFlash) {
            Toast.makeText(getContext(), "Flash unavailable on active camera", Toast.LENGTH_SHORT).show();
            return;
        }

        isFlashOn = !isFlashOn;
        btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        if (imageCapture != null) {
            imageCapture.setFlashMode(isFlashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
        }
    }

    private void setCaptureMode(int mode) {
        if (activeRecording != null) {
            Toast.makeText(getContext(), "Cannot switch mode while recording", Toast.LENGTH_SHORT).show();
            return;
        }

        currentMode = mode;
        isPhotoMode = (mode == MODE_PHOTO);

        btnModePhoto.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_blue));
        btnModeVideo.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_blue));
        if (btnModeFood != null) {
            btnModeFood.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_blue));
        }

        if (layoutFoodScanOverlay != null) {
            layoutFoodScanOverlay.setVisibility(View.GONE);
        }

        if (mode == MODE_PHOTO) {
            btnModePhoto.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_electric_blue));
            vShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner_photo);
        } else if (mode == MODE_VIDEO) {
            btnModeVideo.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_electric_blue));
            vShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner_video);
        } else if (mode == MODE_FOOD_ANALYZE) {
            if (btnModeFood != null) {
                btnModeFood.setTextColor(ContextCompat.getColor(requireContext(), R.color.online_green));
            }
            vShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner_food);
            if (layoutFoodScanOverlay != null) {
                layoutFoodScanOverlay.setVisibility(View.VISIBLE);
            }
        }
    }

    private void takePhoto() {
        if (imageCapture == null || isCapturing) return;

        isCapturing = true;

        // Perform haptic feedback & flash animation
        if (getView() != null) {
            getView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        triggerCaptureFlashAnimation();

        String filename = "FITUP_PHOTO_" + System.currentTimeMillis() + ".jpg";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FitUp");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        isCapturing = false;
                        Uri savedUri = outputFileResults.getSavedUri();
                        if (savedUri != null) {
                            Toast.makeText(getContext(), "Photo saved to Gallery!", Toast.LENGTH_SHORT).show();
                            updateGalleryThumbnail(savedUri);

                            mediaRepository.onPhotoCaptured(savedUri, new MediaRepository.MediaSaveCallback() {
                                @Override
                                public void onSuccess(Uri savedMediaUri) {
                                    Log.d(TAG, "Media repository registered photo: " + savedMediaUri);
                                }

                                @Override
                                public void onError(Exception exception) {
                                    Log.e(TAG, "Media repository error", exception);
                                }
                            });

                            Intent intent = new Intent(requireContext(), SnapPreviewActivity.class);
                            intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, savedUri.toString());
                            intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_PHOTO);
                            startActivity(intent);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturing = false;
                        Log.e(TAG, "Photo capture failed", exception);
                        Toast.makeText(getContext(), "Photo capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void triggerCaptureFlashAnimation() {
        vCaptureFlash.setAlpha(0.8f);
        vCaptureFlash.setVisibility(View.VISIBLE);
        vCaptureFlash.animate()
                .alpha(0.0f)
                .setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        vCaptureFlash.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private void toggleVideoRecording() {
        if (activeRecording != null) {
            stopVideoRecording();
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
                Toast.makeText(getContext(), "Microphone permission required for video recording", Toast.LENGTH_SHORT).show();
                return;
            }
            startVideoRecording();
        }
    }

    private void startVideoRecording() {
        if (videoCapture == null) return;

        String filename = "FITUP_VIDEO_" + System.currentTimeMillis() + ".mp4";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FitUp");

        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build();

        PendingRecording pendingRecording = videoCapture.getOutput()
                .prepareRecording(requireContext(), mediaStoreOutputOptions);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled();
        }

        activeRecording = pendingRecording.start(
                ContextCompat.getMainExecutor(requireContext()),
                videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                        onVideoRecordingFinalized(finalizeEvent);
                    }
                }
        );

        // UI updates during recording
        recordingStartTime = System.currentTimeMillis();
        layoutRecordingTimer.setVisibility(View.VISIBLE);
        vShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner_recording);
        modeSelectorContainer.setAlpha(0.4f);
        modeSelectorContainer.setClickable(false);
        btnCameraSwitch.setClickable(false);
        btnFlash.setClickable(false);

        startRecordingTimer();
    }

    private void startRecordingTimer() {
        // Pulsing red dot animation during recording
        vRecordingDot.animate().alpha(0.2f).setDuration(400).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (activeRecording != null) {
                    vRecordingDot.animate().alpha(1.0f).setDuration(400).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator anim) {
                            if (activeRecording != null) {
                                startPulsingRecordingDot();
                            }
                        }
                    }).start();
                }
            }
        }).start();

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeRecording == null) return;
                long elapsed = System.currentTimeMillis() - recordingStartTime;
                if (elapsed >= MAX_RECORDING_MS) {
                    tvRecordingTimer.setText("00:15 / 00:15");
                    stopVideoRecording();
                } else {
                    long seconds = elapsed / 1000;
                    tvRecordingTimer.setText(String.format(Locale.US, "00:%02d / 00:15", seconds));
                    mainHandler.postDelayed(this, 200);
                }
            }
        };
        mainHandler.post(timerRunnable);
    }

    private void startPulsingRecordingDot() {
        if (activeRecording == null) return;
        vRecordingDot.animate().alpha(0.2f).setDuration(400).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (activeRecording != null) {
                    vRecordingDot.animate().alpha(1.0f).setDuration(400).start();
                }
            }
        }).start();
    }

    private void stopVideoRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }

        if (timerRunnable != null) {
            mainHandler.removeCallbacks(timerRunnable);
        }

        layoutRecordingTimer.setVisibility(View.GONE);
        vShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner_video);
        modeSelectorContainer.setAlpha(1.0f);
        modeSelectorContainer.setClickable(true);
        btnCameraSwitch.setClickable(true);
        btnFlash.setClickable(true);
    }

    private void onVideoRecordingFinalized(VideoRecordEvent.Finalize finalizeEvent) {
        if (!finalizeEvent.hasError()) {
            Uri videoUri = finalizeEvent.getOutputResults().getOutputUri();
            Toast.makeText(getContext(), "15s Video saved to Gallery!", Toast.LENGTH_SHORT).show();
            updateGalleryThumbnail(videoUri);

            mediaRepository.onVideoRecorded(videoUri, new MediaRepository.MediaSaveCallback() {
                @Override
                public void onSuccess(Uri savedMediaUri) {
                    Log.d(TAG, "Media repository registered video: " + savedMediaUri);
                }

                @Override
                public void onError(Exception exception) {
                    Log.e(TAG, "Media repository video error", exception);
                }
            });

            Intent intent = new Intent(requireContext(), SnapPreviewActivity.class);
            intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, videoUri.toString());
            intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_VIDEO);
            startActivity(intent);
        } else {
            Log.e(TAG, "Video recording error code: " + finalizeEvent.getError());
            Toast.makeText(getContext(), "Video recording stopped or error occurred", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateGalleryThumbnail(Uri mediaUri) {
        if (getContext() == null || mediaUri == null) return;
        try {
            ivGalleryIcon.setVisibility(View.GONE);
            ivGalleryThumbnail.setVisibility(View.VISIBLE);
            ivGalleryThumbnail.setImageURI(mediaUri);
        } catch (Exception e) {
            Log.e(TAG, "Unable to load gallery thumbnail", e);
        }
    }

    private void takeFoodPhotoAndAnalyze() {
        if (imageCapture == null || isCapturing) return;

        isCapturing = true;

        if (getView() != null) {
            getView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        triggerCaptureFlashAnimation();

        Toast.makeText(getContext(), "Scanning food & analyzing nutrients...", Toast.LENGTH_SHORT).show();

        String filename = "FITUP_FOOD_" + System.currentTimeMillis() + ".jpg";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FitUp");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        isCapturing = false;
                        Uri savedUri = outputFileResults.getSavedUri();
                        if (savedUri != null) {
                            updateGalleryThumbnail(savedUri);
                            mediaRepository.onPhotoCaptured(savedUri, new MediaRepository.MediaSaveCallback() {
                                @Override
                                public void onSuccess(Uri savedMediaUri) {
                                    Log.d(TAG, "Media repository registered food photo: " + savedMediaUri);
                                }

                                @Override
                                public void onError(Exception exception) {
                                    Log.e(TAG, "Media repository food photo error", exception);
                                }
                            });
                        }
                        showFoodAnalysisDialog(savedUri);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturing = false;
                        Log.e(TAG, "Food photo capture failed", exception);
                        showFoodAnalysisDialog(null);
                    }
                }
        );
    }

    private void showFoodAnalysisDialog(@Nullable Uri photoUri) {
        if (getContext() == null) return;

        BottomSheetDialog dialog =
                new BottomSheetDialog(requireContext());
        dialog.setContentView(R.layout.dialog_food_analysis_sheet);

        if (dialog.getWindow() != null) {
            View bottomSheet = dialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        }

        ImageView ivFoodImage = dialog.findViewById(R.id.iv_sheet_food_image);
        TextView tvTitle = dialog.findViewById(R.id.tv_sheet_food_title);
        TextView tvSubtitle = dialog.findViewById(R.id.tv_sheet_food_subtitle);
        TextView tvGrade = dialog.findViewById(R.id.tv_sheet_grade_badge);

        TextView tvKcal = dialog.findViewById(R.id.tv_sheet_kcal);
        TextView tvProtein = dialog.findViewById(R.id.tv_sheet_protein);
        TextView tvCarbs = dialog.findViewById(R.id.tv_sheet_carbs);
        TextView tvFat = dialog.findViewById(R.id.tv_sheet_fat);

        if (photoUri != null && ivFoodImage != null) {
            ivFoodImage.setImageURI(photoUri);
        }

        String[][] sampleMeals = {
                {"Lay's® Classic Potato Chips", "FreshMart · 170 g container", "A", "90", "16g", "6g", "0g"},
                {"Grilled Chicken & Avocado Bowl", "FitUp Kitchen · 350 g portion", "A+", "520", "42g", "55g", "12g"},
                {"Salmon Quinoa Power Salad", "Healthy Bite · 280 g portion", "A", "450", "36g", "32g", "14g"},
                {"Oatmeal with Protein Shake", "Breakfast Zone · 220 g portion", "A+", "380", "28g", "48g", "6g"}
        };
        int randomIndex = (int) (System.currentTimeMillis() % sampleMeals.length);
        String[] meal = sampleMeals[randomIndex];

        if (tvTitle != null) tvTitle.setText(meal[0]);
        if (tvSubtitle != null) tvSubtitle.setText(meal[1]);
        if (tvGrade != null) tvGrade.setText(meal[2]);
        if (tvKcal != null) tvKcal.setText(meal[3]);
        if (tvProtein != null) tvProtein.setText(meal[4]);
        if (tvCarbs != null) tvCarbs.setText(meal[5]);
        if (tvFat != null) tvFat.setText(meal[6]);

        View btnAddTempSend = dialog.findViewById(R.id.add_temp_send);
        if (btnAddTempSend != null) {
            btnAddTempSend.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(requireContext(), FoodTemplateActivity.class);
                if (photoUri != null) {
                    intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_IMAGE_URI, photoUri.toString());
                }
                intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_TITLE, meal[0]);
                intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_SUBTITLE, meal[1]);
                intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_GRADE, meal[2]);
                intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_KCAL, meal[3]);
                intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_PROTEIN, meal[4]);
                intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_CARBS, meal[5]);
                intent.putExtra(FoodTemplateActivity.EXTRA_FOOD_FAT, meal[6]);
                startActivity(intent);
            });
        }

        View btnShare = dialog.findViewById(R.id.btn_sheet_send_snap);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                dialog.dismiss();
                if (photoUri != null) {
                    Intent intent = new Intent(requireContext(), SnapPreviewActivity.class);
                    intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_URI, photoUri.toString());
                    intent.putExtra(SnapPreviewActivity.EXTRA_MEDIA_TYPE, SnapPreviewActivity.MEDIA_TYPE_PHOTO);
                    startActivity(intent);
                } else {
                    Toast.makeText(getContext(), "Sharing food snap...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        dialog.show();
    }


    @Override
    public void onPause() {
        super.onPause();
        if (activeRecording != null) {
            stopVideoRecording();
        }
        if (timerRunnable != null) {
            mainHandler.removeCallbacks(timerRunnable);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timerRunnable != null) {
            mainHandler.removeCallbacks(timerRunnable);
        }
        if (hideZoomRunnable != null) {
            mainHandler.removeCallbacks(hideZoomRunnable);
        }
    }
}
