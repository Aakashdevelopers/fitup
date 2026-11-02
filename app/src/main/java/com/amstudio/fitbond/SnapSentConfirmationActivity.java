package com.amstudio.fitbond;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Premium Snap Sent Confirmation Screen for Friends Only Private Snap flow.
 * Displays local success visual, preview thumbnail, selected recipient list,
 * privacy confirmation, and navigation actions back to Camera / Chat.
 */
public class SnapSentConfirmationActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_URI = SnapPreviewActivity.EXTRA_MEDIA_URI;
    public static final String EXTRA_MEDIA_TYPE = SnapPreviewActivity.EXTRA_MEDIA_TYPE;
    public static final String EXTRA_RECIPIENT_NAMES = "extra_recipient_names";

    // Delivery UI State enum (ready for future backend integration)
    public enum DeliveryStatus {
        SUCCESS,
        PENDING,
        FAILED
    }

    private View rootLayout;
    private View scrollContainer;
    private View bottomActionsContainer;

    private View layoutSuccessIconRing;
    private View vOuterGlowRing;

    private ImageView ivSentSnapThumbnail;
    private ImageView ivMediaTypeIcon;
    private TextView tvMediaTypeText;

    private TextView tvRecipientCountBadge;
    private RecyclerView rvSentRecipients;

    private MaterialButton btnBackToCamera;
    private MaterialButton btnDone;
    private TextView btnSendAnotherSnap;

    private Uri mediaUri;
    private String mediaType = SnapPreviewActivity.MEDIA_TYPE_PHOTO;
    private final List<SentRecipientModel> recipientsList = new ArrayList<>();
    private final DeliveryStatus currentDeliveryStatus = DeliveryStatus.SUCCESS;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_snap_sent_confirmation);

        // System bar icons styling (light icons for dark theme)
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        initializeViews();
        applyWindowInsets();
        parseIntentData(getIntent());
        setupPreviewContent();
        setupRecipientsList();
        setupClickListeners();
        playSuccessEntranceAnimation();
    }

    private void initializeViews() {
        rootLayout = findViewById(R.id.snap_sent_root);
        scrollContainer = findViewById(R.id.scroll_container);
        bottomActionsContainer = findViewById(R.id.bottom_actions_container);

        layoutSuccessIconRing = findViewById(R.id.layout_success_icon_ring);
        vOuterGlowRing = findViewById(R.id.v_outer_glow_ring);

        ivSentSnapThumbnail = findViewById(R.id.iv_sent_snap_thumbnail);
        ivMediaTypeIcon = findViewById(R.id.iv_media_type_icon);
        tvMediaTypeText = findViewById(R.id.tv_media_type_text);

        tvRecipientCountBadge = findViewById(R.id.tv_recipient_count_badge);
        rvSentRecipients = findViewById(R.id.rv_sent_recipients);

        btnBackToCamera = findViewById(R.id.btn_back_to_camera);
        btnDone = findViewById(R.id.btn_done);
        btnSendAnotherSnap = findViewById(R.id.btn_send_another_snap);
    }

    private void applyWindowInsets() {
        if (rootLayout == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (scrollContainer != null) {
                scrollContainer.setPadding(
                        scrollContainer.getPaddingLeft(),
                        systemBars.top + 12,
                        scrollContainer.getPaddingRight(),
                        scrollContainer.getPaddingBottom()
                );
            }

            if (bottomActionsContainer != null) {
                bottomActionsContainer.setPadding(
                        bottomActionsContainer.getPaddingLeft(),
                        bottomActionsContainer.getPaddingTop(),
                        bottomActionsContainer.getPaddingRight(),
                        systemBars.bottom + 16
                );
            }

            return WindowInsetsCompat.CONSUMED;
        });
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

            ArrayList<String> passedNames = intent.getStringArrayListExtra(EXTRA_RECIPIENT_NAMES);
            if (passedNames != null && !passedNames.isEmpty()) {
                int[] avatarDrawables = {
                        R.drawable.bg_avatar_gradient_1,
                        R.drawable.bg_avatar_gradient_2,
                        R.drawable.bg_avatar_gradient_3,
                        R.drawable.bg_avatar_gradient_4,
                        R.drawable.bg_avatar_gradient_5
                };
                for (int i = 0; i < passedNames.size(); i++) {
                    String name = passedNames.get(i);
                    String username = "@" + name.toLowerCase(Locale.ROOT).replace(" ", "_");
                    int bgRes = avatarDrawables[i % avatarDrawables.length];
                    recipientsList.add(new SentRecipientModel(String.valueOf(i + 1), name, username, bgRes, DeliveryStatus.SUCCESS));
                }
            }
        }

        // Fallback sample recipients for local UI preview
        if (recipientsList.isEmpty()) {
            recipientsList.add(new SentRecipientModel("1", "Jordan Miller", "@jordan_lift", R.drawable.bg_avatar_gradient_1, DeliveryStatus.SUCCESS));
            recipientsList.add(new SentRecipientModel("2", "Sarah Chen", "@sarah_fit", R.drawable.bg_avatar_gradient_2, DeliveryStatus.SUCCESS));
            recipientsList.add(new SentRecipientModel("3", "Marcus Vance", "@marcus_power", R.drawable.bg_avatar_gradient_3, DeliveryStatus.SUCCESS));
        }
    }

    private void setupPreviewContent() {
        if (ivSentSnapThumbnail != null) {
            if (mediaUri != null) {
                ivSentSnapThumbnail.setImageURI(mediaUri);
            } else {
                ivSentSnapThumbnail.setImageResource(R.drawable.bg_sample_gym_preview);
            }
        }

        if (SnapPreviewActivity.MEDIA_TYPE_VIDEO.equals(mediaType)) {
            ivMediaTypeIcon.setImageResource(R.drawable.ic_snap_video);
            tvMediaTypeText.setText(getString(R.string.video_snap_label, "0:15"));
        } else {
            ivMediaTypeIcon.setImageResource(R.drawable.ic_nav_camera);
            tvMediaTypeText.setText(R.string.photo_snap_label);
        }

        if (tvRecipientCountBadge != null) {
            int count = recipientsList.size();
            tvRecipientCountBadge.setText(String.format(Locale.US, "%d friend%s", count, count == 1 ? "" : "s"));
        }
    }

    private void setupRecipientsList() {
        if (rvSentRecipients != null) {
            rvSentRecipients.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            rvSentRecipients.setAdapter(new SentRecipientsAdapter(recipientsList));
        }
    }

    private void setupClickListeners() {
        if (btnBackToCamera != null) {
            btnBackToCamera.setOnClickListener(v -> navigateToMainAppCamera());
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> navigateToMainAppCamera());
        }

        if (btnSendAnotherSnap != null) {
            btnSendAnotherSnap.setOnClickListener(v -> navigateToMainAppCamera());
        }
    }

    private void navigateToMainAppCamera() {
        Intent intent = new Intent(SnapSentConfirmationActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void playSuccessEntranceAnimation() {
        if (layoutSuccessIconRing == null) return;

        layoutSuccessIconRing.setScaleX(0.5f);
        layoutSuccessIconRing.setScaleY(0.5f);
        layoutSuccessIconRing.setAlpha(0.0f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(layoutSuccessIconRing, View.SCALE_X, 0.5f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(layoutSuccessIconRing, View.SCALE_Y, 0.5f, 1.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(layoutSuccessIconRing, View.ALPHA, 0.0f, 1.0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(450);
        set.setInterpolator(new OvershootInterpolator(1.2f));
        set.start();

        if (vOuterGlowRing != null) {
            ObjectAnimator pulseAlpha = ObjectAnimator.ofFloat(vOuterGlowRing, View.ALPHA, 0.15f, 0.40f, 0.25f);
            pulseAlpha.setDuration(1200);
            pulseAlpha.start();
        }
    }

    // Recipient Model
    public static class SentRecipientModel {
        public String id;
        public String name;
        public String username;
        public int avatarBgRes;
        public DeliveryStatus status;

        public SentRecipientModel(String id, String name, String username, int avatarBgRes, DeliveryStatus status) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.avatarBgRes = avatarBgRes;
            this.status = status;
        }
    }

    // RecyclerView Adapter for Recipients
    private static class SentRecipientsAdapter extends RecyclerView.Adapter<SentRecipientsAdapter.RecipientViewHolder> {

        private final List<SentRecipientModel> list;

        public SentRecipientsAdapter(List<SentRecipientModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public RecipientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sent_recipient, parent, false);
            return new RecipientViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecipientViewHolder holder, int position) {
            SentRecipientModel model = list.get(position);
            holder.tvName.setText(model.name);
            holder.ivAvatar.setBackgroundResource(model.avatarBgRes);

            if (model.status == DeliveryStatus.SUCCESS) {
                holder.tvStatus.setText(R.string.status_delivered);
            } else if (model.status == DeliveryStatus.PENDING) {
                holder.tvStatus.setText(R.string.status_sending);
            } else {
                holder.tvStatus.setText(R.string.status_failed);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class RecipientViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            TextView tvStatus;

            public RecipientViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_recipient_avatar);
                tvName = itemView.findViewById(R.id.tv_recipient_name);
                tvStatus = itemView.findViewById(R.id.tv_delivery_status);
            }
        }
    }
}
