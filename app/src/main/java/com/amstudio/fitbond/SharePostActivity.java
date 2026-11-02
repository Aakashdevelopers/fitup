package com.amstudio.fitbond;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.amstudio.fitbond.repository.UserRepository;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SharePostActivity extends AppCompatActivity {

    private static final String TAG = "SharePostActivity";

    private ImageButton btnBack;
    private ImageView ivSharePreview;
    private EditText etTitle;
    private EditText etCaption;
    private MaterialButton btnShare;

    private Uri mediaUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_share_post);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        View rootLayout = findViewById(R.id.root_share_post);
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
        setupClickListeners();
        loadPreviewImage();
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
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back_share);
        ivSharePreview = findViewById(R.id.iv_share_preview);
        etTitle = findViewById(R.id.et_post_title);
        etCaption = findViewById(R.id.et_post_caption);
        btnShare = findViewById(R.id.btn_share_post);
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (ivSharePreview != null) {
            ivSharePreview.setOnClickListener(v -> openFullImagePreview());
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> publishPostToFirestore());
        }
    }

    private void openFullImagePreview() {
        if (mediaUri == null) return;
        ArrayList<String> uris = new ArrayList<>();
        uris.add(mediaUri.toString());
        String title = (etTitle != null && !TextUtils.isEmpty(etTitle.getText())) ?
                etTitle.getText().toString() : "New post";
        GalleryPreviewActivity.open(this, uris, null, title);
    }

    private void loadPreviewImage() {
        if (ivSharePreview != null && mediaUri != null) {
            Glide.with(this)
                    .load(mediaUri)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .into(ivSharePreview);
        }
    }

    private void publishPostToFirestore() {
        String currentUid = UserRepository.getInstance().getCurrentUid();
        if (currentUid == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = etTitle != null ? etTitle.getText().toString().trim() : "";
        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(caption)) {
            title = "My Workout Post";
        }

        btnShare.setEnabled(false);
        Toast.makeText(this, "Sharing post...", Toast.LENGTH_SHORT).show();

        String snapId = UUID.randomUUID().toString();
        if (mediaUri != null) {
            uploadMediaAndSavePost(currentUid, snapId, title, caption, mediaUri);
        } else {
            savePostMetadataToFirestore(currentUid, snapId, title, caption, "");
        }
    }

    private void uploadMediaAndSavePost(String currentUid, String snapId, String title, String caption, Uri uri) {
        StorageReference ref = FirebaseStorage.getInstance().getReference().child("snaps/" + snapId + "/media.jpg");
        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                    savePostMetadataToFirestore(currentUid, snapId, title, caption, downloadUrl.toString());
                }).addOnFailureListener(e -> {
                    Log.w(TAG, "Get download URL failed, using local URI fallback", e);
                    savePostMetadataToFirestore(currentUid, snapId, title, caption, uri.toString());
                }))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Storage upload failed, using local URI fallback", e);
                    savePostMetadataToFirestore(currentUid, snapId, title, caption, uri.toString());
                });
    }

    private void savePostMetadataToFirestore(String currentUid, String snapId, String title, String caption, String mediaUrl) {
        Map<String, Object> postMap = new HashMap<>();
        postMap.put("id", snapId);
        postMap.put("senderUid", currentUid);
        postMap.put("title", title);
        postMap.put("caption", caption);
        postMap.put("mediaUrl", mediaUrl);
        postMap.put("mediaType", "PHOTO");
        postMap.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("snaps").document(snapId)
                .set(postMap)
                .addOnSuccessListener(aVoid -> {
                    UserRepository.getInstance().invalidateCache();
                    Toast.makeText(this, "Post shared successfully!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(this, MainActivity.class);
                    intent.putExtra("ACTION_NAVIGATE_TAG", MainActivity.TAG_PROFILE);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnShare.setEnabled(true);
                    Log.e(TAG, "Failed to share post", e);
                    Toast.makeText(this, "Failed to share post: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
