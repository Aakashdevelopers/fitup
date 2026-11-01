package com.amstudio.fitup;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.amstudio.fitup.model.SnapMetadata;
import com.amstudio.fitup.repository.SnapRepository;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.File;

public class SnapViewerDialog extends DialogFragment {

    private static final String TAG = "SnapViewerDialog";

    private static final String ARG_NAME = "arg_name";
    private static final String ARG_INITIALS = "arg_initials";
    private static final String ARG_GRADIENT_RES = "arg_gradient_res";
    private static final String ARG_IS_VIDEO = "arg_is_video";
    private static final String ARG_DURATION = "arg_duration";
    private static final String ARG_SNAP_ID = "arg_snap_id";
    private static final String ARG_MEDIA_URL = "arg_media_url";

    private ProgressBar pbTimer;
    private ProgressBar pbLoading;
    private TextView tvError;
    private TextView tvAvatarInitials;
    private TextView tvUserName;
    private ImageView ivSnapIcon;
    private TextView tvSnapLabel;
    private ImageButton btnClose;
    private ImageView ivSnapMedia;
    private VideoView vvSnapVideo;

    private CountDownTimer countDownTimer;
    private boolean timerStarted = false;
    private boolean isFirestoreFallbackAttempted = false;
    private long durationMs = 10000;

    public static SnapViewerDialog newInstance(String name, String initials, int gradientRes, boolean isVideo, String duration) {
        return newInstance(name, initials, gradientRes, isVideo, duration, null, null);
    }

    public static SnapViewerDialog newInstance(String name, String initials, int gradientRes, boolean isVideo, String duration, String snapId, String mediaUrl) {
        SnapViewerDialog fragment = new SnapViewerDialog();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putString(ARG_INITIALS, initials);
        args.putInt(ARG_GRADIENT_RES, gradientRes);
        args.putBoolean(ARG_IS_VIDEO, isVideo);
        args.putString(ARG_DURATION, duration);
        if (snapId != null) args.putString(ARG_SNAP_ID, snapId);
        if (mediaUrl != null) args.putString(ARG_MEDIA_URL, mediaUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_snap_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pbTimer = view.findViewById(R.id.pb_snap_timer);
        pbLoading = view.findViewById(R.id.pb_snap_loading);
        tvError = view.findViewById(R.id.tv_snap_error);
        tvAvatarInitials = view.findViewById(R.id.tv_dialog_avatar);
        tvUserName = view.findViewById(R.id.tv_dialog_user_name);
        ivSnapIcon = view.findViewById(R.id.iv_dialog_snap_icon);
        tvSnapLabel = view.findViewById(R.id.tv_dialog_snap_label);
        btnClose = view.findViewById(R.id.btn_dialog_close);
        ivSnapMedia = view.findViewById(R.id.iv_snap_preview_media);
        vvSnapVideo = view.findViewById(R.id.vv_snap_preview_video);

        Bundle args = getArguments();
        if (args != null) {
            String name = args.getString(ARG_NAME, "Friend");
            String initials = args.getString(ARG_INITIALS, "F");
            int gradientRes = args.getInt(ARG_GRADIENT_RES, R.drawable.bg_avatar_gradient_1);
            boolean isVideo = args.getBoolean(ARG_IS_VIDEO, false);
            String durationStr = args.getString(ARG_DURATION, "10s");
            String snapId = args.getString(ARG_SNAP_ID);
            String mediaUrl = args.getString(ARG_MEDIA_URL);

            parseDuration(durationStr);

            if (tvUserName != null) tvUserName.setText(name);
            if (tvAvatarInitials != null) {
                tvAvatarInitials.setText(initials);
                tvAvatarInitials.setBackgroundResource(gradientRes);
            }

            if (tvSnapLabel != null) {
                tvSnapLabel.setText(isVideo ? "Private Video Snap" : "Private Photo Snap");
            }
            if (ivSnapIcon != null) {
                ivSnapIcon.setImageResource(isVideo ? R.drawable.ic_snap_video : R.drawable.ic_nav_camera);
            }

            if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
                if (isVideo) {
                    loadVideoSnap(mediaUrl, snapId);
                } else {
                    loadPhotoSnap(mediaUrl, snapId);
                }
            } else if (snapId != null && !snapId.trim().isEmpty()) {
                fetchAndLoadFromFirestore(snapId, isVideo);
            } else {
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                showError("Snap media not available");
            }
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    private void parseDuration(String durationStr) {
        if (durationStr == null) {
            durationMs = 10000;
            return;
        }
        try {
            String cleaned = durationStr.replace("s", "").trim();
            if (cleaned.contains(":")) {
                String[] parts = cleaned.split(":");
                long sec = Long.parseLong(parts[parts.length - 1]);
                durationMs = Math.max(sec * 1000, 3000);
            } else {
                long sec = Long.parseLong(cleaned);
                durationMs = Math.max(sec * 1000, 3000);
            }
        } catch (Exception e) {
            durationMs = 10000;
        }
    }

    private void loadPhotoSnap(String url, String snapId) {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
        if (tvError != null) tvError.setVisibility(View.GONE);
        if (ivSnapMedia != null) ivSnapMedia.setVisibility(View.GONE);
        if (vvSnapVideo != null) vvSnapVideo.setVisibility(View.GONE);

        if (url == null || url.trim().isEmpty()) {
            if (!isFirestoreFallbackAttempted && snapId != null && !snapId.trim().isEmpty()) {
                isFirestoreFallbackAttempted = true;
                fetchAndLoadFromFirestore(snapId, false);
            } else {
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                showError("Photo URL not available");
            }
            return;
        }

        Context ctx = getContext();
        if (ctx == null && getActivity() != null) {
            ctx = getActivity().getApplicationContext();
        }
        if (ctx == null) {
            if (pbLoading != null) pbLoading.setVisibility(View.GONE);
            showError("Unable to load photo");
            return;
        }

        Object loadTarget = ChatMessageAdapter.getLocalFileOrTarget(url, ctx);

        Glide.with(ctx)
                .load(loadTarget)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.IMMEDIATE)
                .dontAnimate()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.w(TAG, "Glide photo load failed for url: " + url, e);
                        if (getActivity() == null || getActivity().isFinishing()) return false;
                        if (!isFirestoreFallbackAttempted && snapId != null && !snapId.trim().isEmpty()) {
                            isFirestoreFallbackAttempted = true;
                            fetchAndLoadFromFirestore(snapId, false);
                        } else {
                            if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                            showError("Unable to load photo");
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        if (getActivity() == null || getActivity().isFinishing()) return false;
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        if (ivSnapMedia != null) ivSnapMedia.setVisibility(View.VISIBLE);
                        startTimer();
                        return false;
                    }
                })
                .into(ivSnapMedia);
    }

    private void loadVideoSnap(String url, String snapId) {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
        if (tvError != null) tvError.setVisibility(View.GONE);
        if (ivSnapMedia != null) ivSnapMedia.setVisibility(View.GONE);

        if (vvSnapVideo != null) {
            vvSnapVideo.setVisibility(View.VISIBLE);
            Context ctx = getContext() != null ? getContext() : (getActivity() != null ? getActivity().getApplicationContext() : null);
            Object target = ChatMessageAdapter.getLocalFileOrTarget(url, ctx);
            if (target instanceof File) {
                vvSnapVideo.setVideoPath(((File) target).getAbsolutePath());
            } else if (target instanceof Uri) {
                vvSnapVideo.setVideoURI((Uri) target);
            } else {
                vvSnapVideo.setVideoURI(Uri.parse(url));
            }

            vvSnapVideo.setOnPreparedListener(mp -> {
                if (!isAdded()) return;
                mp.setLooping(false);
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                vvSnapVideo.start();
                startTimer();
            });

            vvSnapVideo.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "VideoView playback error: " + what + ", " + extra);
                if (!isAdded()) return true;
                if (!isFirestoreFallbackAttempted && snapId != null && !snapId.trim().isEmpty()) {
                    isFirestoreFallbackAttempted = true;
                    fetchAndLoadFromFirestore(snapId, true);
                } else {
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                    showError("Unable to play snap video");
                }
                return true;
            });

            vvSnapVideo.setOnCompletionListener(mp -> {
                if (isAdded()) {
                    dismissAllowingStateLoss();
                }
            });
        }
    }

    private void fetchAndLoadFromFirestore(String snapId, boolean isVideoHint) {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
        if (tvError != null) tvError.setVisibility(View.GONE);

        if (snapId == null || snapId.trim().isEmpty()) {
            if (pbLoading != null) pbLoading.setVisibility(View.GONE);
            showError("Media not available");
            return;
        }

        String cleanSnapId = snapId.startsWith("snap_") ? snapId.substring(5) : snapId;

        SnapRepository.getInstance().getSnapMetadata(cleanSnapId, new SnapRepository.SnapDetailsCallback() {
            @Override
            public void onSuccess(SnapMetadata metadata) {
                if (!isAdded()) return;
                if (metadata != null && metadata.getMediaUrl() != null && !metadata.getMediaUrl().trim().isEmpty()) {
                    boolean isVideo = SnapMetadata.MEDIA_TYPE_VIDEO.equalsIgnoreCase(metadata.getMediaType()) || isVideoHint;
                    String mediaUrl = metadata.getMediaUrl();
                    if (isVideo) {
                        loadVideoSnap(mediaUrl, null);
                    } else {
                        loadPhotoSnap(mediaUrl, null);
                    }
                } else {
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                    showError("Snap media not found");
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                if (!cleanSnapId.equals(snapId)) {
                    SnapRepository.getInstance().getSnapMetadata(snapId, new SnapRepository.SnapDetailsCallback() {
                        @Override
                        public void onSuccess(SnapMetadata metadata) {
                            if (!isAdded()) return;
                            if (metadata != null && metadata.getMediaUrl() != null && !metadata.getMediaUrl().trim().isEmpty()) {
                                boolean isVideo = SnapMetadata.MEDIA_TYPE_VIDEO.equalsIgnoreCase(metadata.getMediaType()) || isVideoHint;
                                if (isVideo) {
                                    loadVideoSnap(metadata.getMediaUrl(), null);
                                } else {
                                    loadPhotoSnap(metadata.getMediaUrl(), null);
                                }
                            } else {
                                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                                showError("Snap media not found");
                            }
                        }

                        @Override
                        public void onError(Exception e2) {
                            if (!isAdded()) return;
                            if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                            showError("Unable to load snap media");
                        }
                    });
                } else {
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                    showError("Unable to load snap media");
                }
            }
        });
    }

    private void showError(String message) {
        if (tvError != null) {
            tvError.setText(message);
            tvError.setVisibility(View.VISIBLE);
        }
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

    private void startTimer() {
        if (timerStarted) return;
        timerStarted = true;

        if (pbTimer != null) {
            pbTimer.setProgress(100);
        }

        countDownTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (pbTimer != null) {
                    int progress = (int) ((millisUntilFinished / (float) durationMs) * 100);
                    pbTimer.setProgress(progress);
                }
            }

            @Override
            public void onFinish() {
                if (pbTimer != null) {
                    pbTimer.setProgress(0);
                }
                dismissAllowingStateLoss();
            }
        }.start();
    }

    @Override
    public void onDestroyView() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (vvSnapVideo != null) {
            vvSnapVideo.stopPlayback();
        }
        super.onDestroyView();
    }
}
