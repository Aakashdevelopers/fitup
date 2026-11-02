package com.amstudio.fitbond;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;

/**
 * Premium Snap Preview Activity for Friends Only Private Snap feature.
 * Provides full-screen photo & video preview with auto-detection, visual editing tools,
 * privacy branding, retake/discard controls, and navigation to Private Friend Selection.
 */
public class SnapPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_URI = "extra_media_uri";
    public static final String EXTRA_MEDIA_TYPE = "extra_media_type";
    public static final String MEDIA_TYPE_PHOTO = "photo";
    public static final String MEDIA_TYPE_VIDEO = "video";

    private static final String TAG = "SnapPreviewActivity";

    // View References
    private View rootLayout;
    private View topNavBar;
    private View bottomActionSection;
    private ImageButton btnBackClose;
    private ImageView ivPreviewPhoto;
    private VideoView vvPreviewVideo;
    private View videoControlOverlay;
    private ImageButton btnVideoPlayPause;
    private ProgressBar pbVideoProgress;
    private TextView tvVideoTimer;
    private TextView tvCaptionOverlay;

    private ImageButton btnToolText;
    private ImageButton btnToolCrop;
    private ImageButton btnToolDraw;
    private ImageButton btnToolSticker;

    private ImageButton btnDiscard;
    private View btnRetake;
    private View btnSendToFriends;

    // State Variables
    private Uri mediaUri;
    private String mediaType = MEDIA_TYPE_PHOTO;
    private boolean isVideoPlaying = false;
    private boolean isCaptionVisible = false;

    private final Handler videoHandler = new Handler(Looper.getMainLooper());
    private Runnable videoProgressRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_snap_preview);

        // System Bar Window Insets setup for immersive display
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        initializeViews();
        applyWindowInsets();
        parseIntentData(getIntent());
        setupClickListeners();
        setupPreviewState();
    }

    private void initializeViews() {
        rootLayout = findViewById(R.id.snap_preview_root);
        topNavBar = findViewById(R.id.top_nav_bar);
        bottomActionSection = findViewById(R.id.bottom_action_section);
        btnBackClose = findViewById(R.id.btn_back_close);

        ivPreviewPhoto = findViewById(R.id.iv_preview_photo);
        vvPreviewVideo = findViewById(R.id.vv_preview_video);
        videoControlOverlay = findViewById(R.id.video_control_overlay);
        btnVideoPlayPause = findViewById(R.id.btn_video_play_pause);
        pbVideoProgress = findViewById(R.id.pb_video_progress);
        tvVideoTimer = findViewById(R.id.tv_video_timer);
        tvCaptionOverlay = findViewById(R.id.tv_caption_overlay);

        btnToolText = findViewById(R.id.btn_tool_text);
        btnToolCrop = findViewById(R.id.btn_tool_crop);
        btnToolDraw = findViewById(R.id.btn_tool_draw);
        btnToolSticker = findViewById(R.id.btn_tool_sticker);

        btnDiscard = findViewById(R.id.btn_discard);
        btnRetake = findViewById(R.id.btn_retake);
        btnSendToFriends = findViewById(R.id.btn_send_to_friends);
    }

    private void applyWindowInsets() {
        if (rootLayout == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topNavBar != null) {
                topNavBar.setPadding(
                        topNavBar.getPaddingLeft(),
                        systemBars.top + 12,
                        topNavBar.getPaddingRight(),
                        topNavBar.getPaddingBottom()
                );
            }

            if (bottomActionSection != null) {
                bottomActionSection.setPadding(
                        bottomActionSection.getPaddingLeft(),
                        bottomActionSection.getPaddingTop(),
                        bottomActionSection.getPaddingRight(),
                        Math.max(systemBars.bottom + 12, 20)
                );
            }

            return insets;
        });

        if (rootLayout.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(rootLayout);
        } else {
            rootLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {}
            });
        }
    }

    private void parseIntentData(@Nullable Intent intent) {
        if (intent != null) {
            String uriStr = intent.getStringExtra(EXTRA_MEDIA_URI);
            if (uriStr != null) {
                mediaUri = Uri.parse(uriStr);
            }
            String typeExtra = intent.getStringExtra(EXTRA_MEDIA_TYPE);
            if (typeExtra != null) {
                mediaType = typeExtra;
            }
        }

        // Automatic Media Type Detection if URI is available
        if (mediaUri != null) {
            String detectedType = detectMediaType(mediaUri);
            if (detectedType != null) {
                mediaType = detectedType;
            }
        }
    }

    /**
     * Automatically detects whether media URI is a video or photo.
     */
    private String detectMediaType(@NonNull Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            String mimeType = resolver.getType(uri);
            if (mimeType != null) {
                if (mimeType.startsWith("video")) {
                    return MEDIA_TYPE_VIDEO;
                } else if (mimeType.startsWith("image")) {
                    return MEDIA_TYPE_PHOTO;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving MIME type from ContentResolver", e);
        }

        // Fallback URI string check
        String uriPath = uri.toString().toLowerCase(Locale.ROOT);
        if (uriPath.endsWith(".mp4") || uriPath.endsWith(".mkv") || uriPath.endsWith(".mov") || uriPath.endsWith(".3gp") || uriPath.contains("video")) {
            return MEDIA_TYPE_VIDEO;
        } else if (uriPath.endsWith(".jpg") || uriPath.endsWith(".jpeg") || uriPath.endsWith(".png") || uriPath.endsWith(".webp") || uriPath.contains("photo") || uriPath.contains("image") || uriPath.contains("pictures")) {
            return MEDIA_TYPE_PHOTO;
        }

        return null;
    }

    private void setupClickListeners() {
        if (btnBackClose != null) {
            btnBackClose.setOnClickListener(v -> finish());
        }

        // Editing Toolbar Placeholders
        if (btnToolText != null) {
            btnToolText.setOnClickListener(v -> {
                isCaptionVisible = !isCaptionVisible;
                tvCaptionOverlay.setVisibility(isCaptionVisible ? View.VISIBLE : View.GONE);
                Toast.makeText(this, isCaptionVisible ? "Text Caption Added" : "Text Caption Hidden", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnToolCrop != null) {
            btnToolCrop.setOnClickListener(v -> Toast.makeText(this, "Crop Tool: Adjust snap framing", Toast.LENGTH_SHORT).show());
        }

        if (btnToolDraw != null) {
            btnToolDraw.setOnClickListener(v -> Toast.makeText(this, "Drawing Brush: Canvas active", Toast.LENGTH_SHORT).show());
        }

        if (btnToolSticker != null) {
            btnToolSticker.setOnClickListener(v -> Toast.makeText(this, "Sticker Library coming soon", Toast.LENGTH_SHORT).show());
        }

        // Video Play/Pause Overlay Click
        if (btnVideoPlayPause != null) {
            btnVideoPlayPause.setOnClickListener(v -> toggleVideoPlayback());
        }

        // Action Buttons
        if (btnDiscard != null) {
            btnDiscard.setOnClickListener(v -> showDiscardConfirmationDialog());
        }

        if (btnRetake != null) {
            btnRetake.setOnClickListener(v -> {
                setResult(RESULT_CANCELED);
                finish();
            });
        }

        if (btnSendToFriends != null) {
            btnSendToFriends.setOnClickListener(v -> navigateToSelectFriends());
        }
    }

    private void setupPreviewState() {
        applyPreviewMode(mediaType);
    }

    private void applyPreviewMode(String targetMode) {
        this.mediaType = targetMode;

        if (MEDIA_TYPE_PHOTO.equals(targetMode)) {
            stopVideoPlayback();
            vvPreviewVideo.setVisibility(View.GONE);
            videoControlOverlay.setVisibility(View.GONE);

            ivPreviewPhoto.setVisibility(View.VISIBLE);
            if (mediaUri != null) {
                ivPreviewPhoto.setImageURI(mediaUri);
            } else {
                ivPreviewPhoto.setImageResource(R.drawable.bg_sample_gym_preview);
            }

        } else {
            ivPreviewPhoto.setVisibility(View.GONE);
            vvPreviewVideo.setVisibility(View.VISIBLE);
            videoControlOverlay.setVisibility(View.VISIBLE);

            setupVideoPlayer();
        }
    }

    private void setupVideoPlayer() {
        if (mediaUri != null) {
            vvPreviewVideo.setVideoURI(mediaUri);
        } else {
            Log.d(TAG, "No video Uri provided, video mode active with sample overlay");
        }

        vvPreviewVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            int durationMs = mp.getDuration();
            pbVideoProgress.setMax(durationMs > 0 ? durationMs : 15000);
            startVideoPlayback();
        });

        vvPreviewVideo.setOnCompletionListener(mp -> {
            btnVideoPlayPause.setImageResource(R.drawable.ic_play);
            isVideoPlaying = false;
        });

        startVideoPlayback();
    }

    private void toggleVideoPlayback() {
        if (isVideoPlaying) {
            pauseVideoPlayback();
        } else {
            startVideoPlayback();
        }
    }

    private void startVideoPlayback() {
        vvPreviewVideo.start();
        isVideoPlaying = true;
        btnVideoPlayPause.setImageResource(R.drawable.ic_pause);
        startVideoProgressTracker();
    }

    private void pauseVideoPlayback() {
        vvPreviewVideo.pause();
        isVideoPlaying = false;
        btnVideoPlayPause.setImageResource(R.drawable.ic_play);
        stopVideoProgressTracker();
    }

    private void stopVideoPlayback() {
        if (vvPreviewVideo.isPlaying()) {
            vvPreviewVideo.stopPlayback();
        }
        isVideoPlaying = false;
        stopVideoProgressTracker();
    }

    private void startVideoProgressTracker() {
        stopVideoProgressTracker();
        videoProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (vvPreviewVideo != null && isVideoPlaying) {
                    int currentPosition = vvPreviewVideo.getCurrentPosition();
                    int duration = vvPreviewVideo.getDuration();

                    if (duration <= 0) duration = 15000;

                    pbVideoProgress.setProgress(currentPosition);

                    long currentSec = currentPosition / 1000;
                    long totalSec = duration / 1000;
                    tvVideoTimer.setText(String.format(Locale.US, "00:%02d / 00:%02d", currentSec, totalSec));

                    videoHandler.postDelayed(this, 250);
                }
            }
        };
        videoHandler.post(videoProgressRunnable);
    }

    private void stopVideoProgressTracker() {
        if (videoProgressRunnable != null) {
            videoHandler.removeCallbacks(videoProgressRunnable);
            videoProgressRunnable = null;
        }
    }

    private void showDiscardConfirmationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_discard_confirmation);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_dialog_discard).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(SnapPreviewActivity.this, "Snap discarded", Toast.LENGTH_SHORT).show();
            finish();
        });

        dialog.show();
    }

    private void navigateToSelectFriends() {
        Intent intent = new Intent(SnapPreviewActivity.this, SelectFriendsActivity.class);
        if (mediaUri != null) {
            intent.putExtra(EXTRA_MEDIA_URI, mediaUri.toString());
        }
        intent.putExtra(EXTRA_MEDIA_TYPE, mediaType);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseVideoPlayback();
    }

    @Override
    protected void onDestroy() {
        stopVideoPlayback();
        super.onDestroy();
    }
}
