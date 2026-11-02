package com.amstudio.fitbond;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.UserRepository;
import com.amstudio.fitbond.util.AvatarLoader;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private static final long DEBOUNCE_DELAY_MS = 500;

    // Views
    private ImageButton btnBack;
    private MaterialButton btnSaveTop;
    private ShapeableImageView ivEditAvatar;
    private View btnEditPhotoBadge;
    private TextView btnChangePhoto;
    private TextView btnRemovePhoto;
    private TextView tvPhotoDot;

    private TextInputEditText etFullName;
    private TextInputEditText etUsername;
    private TextView tvUsernameStatus;
    private TextInputEditText etBio;
    private AutoCompleteTextView actvGender;
    private TextInputEditText etDob;
    private TextInputEditText etLocation;

    private MaterialButton btnSaveChanges;
    private View layoutLoadingOverlay;
    private TextView tvLoadingText;

    // State
    private UserProfile currentProfile;
    private Bitmap pendingAvatarBitmap;
    private boolean isPhotoChanged = false;
    private boolean isPhotoRemoved = false;
    private boolean isUsernameValid = true;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable checkUsernameRunnable;

    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        View rootLayout = findViewById(R.id.edit_profile_root);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        initPhotoPickerLauncher();
        initViews();
        setupGenderDropdown();
        setupDobPicker();
        setupClickListeners();
        setupUsernameValidation();

        loadProfileData();
    }

    private ActivityResultLauncher<String> getContentLauncher;

    private void initPhotoPickerLauncher() {
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::handleSelectedImageUri
        );

        getContentLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleSelectedImageUri
        );
    }

    private void handleSelectedImageUri(Uri uri) {
        if (uri != null) {
            Bitmap bitmap = loadBitmapFromUri(uri);
            if (bitmap != null) {
                pendingAvatarBitmap = compressBitmapIfNeeded(bitmap);
                isPhotoChanged = true;
                isPhotoRemoved = false;

                ivEditAvatar.setImageBitmap(pendingAvatarBitmap);
                updatePhotoActionButtons(true);
                Toast.makeText(this, "New profile photo selected", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Could not load selected image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        if (uri == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, s) -> decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)));
            } else {
                java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (inputStream != null) inputStream.close();
                return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "ImageDecoder failed, attempting InputStream fallback", e);
            try {
                java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (inputStream != null) inputStream.close();
                return bitmap;
            } catch (Exception ex) {
                Log.e(TAG, "InputStream fallback failed", ex);
                return null;
            }
        }
    }

    private Bitmap compressBitmapIfNeeded(Bitmap original) {
        int maxDim = 1080;
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxDim && height <= maxDim) {
            return original;
        }

        float ratio = (float) width / (float) height;
        int newWidth;
        int newHeight;

        if (width > height) {
            newWidth = maxDim;
            newHeight = Math.round(maxDim / ratio);
        } else {
            newHeight = maxDim;
            newWidth = Math.round(maxDim * ratio);
        }

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnSaveTop = findViewById(R.id.btn_save_top);
        ivEditAvatar = findViewById(R.id.iv_edit_avatar);
        btnEditPhotoBadge = findViewById(R.id.btn_edit_photo_badge);
        btnChangePhoto = findViewById(R.id.btn_change_photo);
        btnRemovePhoto = findViewById(R.id.btn_remove_photo);
        tvPhotoDot = findViewById(R.id.tv_photo_dot);

        etFullName = findViewById(R.id.et_full_name);
        etUsername = findViewById(R.id.et_username);
        tvUsernameStatus = findViewById(R.id.tv_username_status);
        etBio = findViewById(R.id.et_bio);
        actvGender = findViewById(R.id.actv_gender);
        etDob = findViewById(R.id.et_dob);
        etLocation = findViewById(R.id.et_location);

        btnSaveChanges = findViewById(R.id.btn_save_changes);
        layoutLoadingOverlay = findViewById(R.id.layout_loading_overlay);
        tvLoadingText = findViewById(R.id.tv_loading_text);
    }

    private void setupGenderDropdown() {
        String[] genderOptions = new String[]{"Male", "Female", "Non-binary", "Prefer not to say"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genderOptions);
        actvGender.setAdapter(adapter);
    }

    private void setupDobPicker() {
        etDob.setOnClickListener(v -> showDatePickerDialog());
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();

        if (currentProfile != null && !TextUtils.isEmpty(currentProfile.getDateOfBirth())) {
            try {
                String[] parts = currentProfile.getDateOfBirth().split("-");
                if (parts.length == 3) {
                    calendar.set(Calendar.YEAR, Integer.parseInt(parts[0]));
                    calendar.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
                    calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
                }
            } catch (Exception ignored) {
            }
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, yearSelected, monthSelected, daySelected) -> {
                    String formattedDate = String.format(Locale.US, "%04d-%02d-%02d", yearSelected, monthSelected + 1, daySelected);
                    etDob.setText(formattedDate);
                },
                year, month, day
        );

        // Disallow future dates
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        View.OnClickListener photoClickListener = v -> {
            try {
                photoPickerLauncher.launch(
                        new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()
                );
            } catch (Exception e) {
                Log.w(TAG, "PickVisualMedia launcher failed, trying GetContent", e);
                if (getContentLauncher != null) {
                    getContentLauncher.launch("image/*");
                }
            }
        };

        btnChangePhoto.setOnClickListener(photoClickListener);
        btnEditPhotoBadge.setOnClickListener(photoClickListener);
        ivEditAvatar.setOnClickListener(photoClickListener);

        btnRemovePhoto.setOnClickListener(v -> {
            pendingAvatarBitmap = null;
            isPhotoChanged = true;
            isPhotoRemoved = true;
            ivEditAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
            updatePhotoActionButtons(false);
            Toast.makeText(this, "Profile photo removed", Toast.LENGTH_SHORT).show();
        });

        btnSaveTop.setOnClickListener(v -> validateAndSaveProfile());
        btnSaveChanges.setOnClickListener(v -> validateAndSaveProfile());
    }

    private void updatePhotoActionButtons(boolean hasCustomPhoto) {
        if (hasCustomPhoto) {
            btnRemovePhoto.setVisibility(View.VISIBLE);
            tvPhotoDot.setVisibility(View.VISIBLE);
        } else {
            btnRemovePhoto.setVisibility(View.GONE);
            tvPhotoDot.setVisibility(View.GONE);
        }
    }

    private void setupUsernameValidation() {
        etUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (checkUsernameRunnable != null) {
                    debounceHandler.removeCallbacks(checkUsernameRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String input = s != null ? s.toString().trim() : "";

                if (currentProfile != null && input.equalsIgnoreCase(currentProfile.getUsername())) {
                    setUIVisibleUsernameStatus(true, "Your current username", ContextCompat.getColor(EditProfileActivity.this, R.color.electric_blue));
                    isUsernameValid = true;
                    return;
                }

                String cleanUsername = input.toLowerCase().replaceAll("[^a-z0-9_]", "");
                if (cleanUsername.length() < 3 || cleanUsername.length() > 30) {
                    setUIVisibleUsernameStatus(false, "3–30 characters: lowercase, numbers, underscores", ContextCompat.getColor(EditProfileActivity.this, R.color.logout_red));
                    isUsernameValid = false;
                    return;
                }

                setUIVisibleUsernameStatus(false, "Checking availability…", ContextCompat.getColor(EditProfileActivity.this, R.color.light_blue));

                checkUsernameRunnable = () -> UserRepository.getInstance().checkUsernameAvailability(cleanUsername, new UserRepository.UsernameAvailabilityCallback() {
                    @Override
                    public void onResult(boolean isAvailable, boolean isCurrent, String message) {
                        if (isFinishing() || isDestroyed()) return;

                        if (isCurrent) {
                            setUIVisibleUsernameStatus(true, "Your current username", ContextCompat.getColor(EditProfileActivity.this, R.color.electric_blue));
                            isUsernameValid = true;
                        } else if (isAvailable) {
                            setUIVisibleUsernameStatus(true, "✓ " + message, ContextCompat.getColor(EditProfileActivity.this, R.color.electric_blue));
                            isUsernameValid = true;
                        } else {
                            setUIVisibleUsernameStatus(false, "✕ " + message, ContextCompat.getColor(EditProfileActivity.this, R.color.logout_red));
                            isUsernameValid = false;
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (isFinishing() || isDestroyed()) return;
                        setUIVisibleUsernameStatus(false, "Error checking availability", ContextCompat.getColor(EditProfileActivity.this, R.color.logout_red));
                        isUsernameValid = false;
                    }
                });

                debounceHandler.postDelayed(checkUsernameRunnable, DEBOUNCE_DELAY_MS);
            }
        });
    }

    private void setUIVisibleUsernameStatus(boolean valid, String text, int color) {
        tvUsernameStatus.setText(text);
        tvUsernameStatus.setTextColor(color);
        btnSaveTop.setEnabled(valid);
        btnSaveChanges.setEnabled(valid);
    }

    private void loadProfileData() {
        showLoadingOverlay("Loading profile…");
        String uid = UserRepository.getInstance().getCurrentUid();

        if (uid == null) {
            hideLoadingOverlay();
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        UserRepository.getInstance().getUserProfile(uid, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                hideLoadingOverlay();
                currentProfile = profile;
                populateProfileFields(profile);
            }

            @Override
            public void onError(Exception e) {
                hideLoadingOverlay();
                Log.e(TAG, "Failed to load user profile", e);
                Toast.makeText(EditProfileActivity.this, "Failed to load profile data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateProfileFields(UserProfile profile) {
        if (profile == null) return;

        if (!TextUtils.isEmpty(profile.getDisplayName())) {
            etFullName.setText(profile.getDisplayName());
        }
        if (!TextUtils.isEmpty(profile.getUsername())) {
            etUsername.setText(profile.getUsername());
        }
        if (!TextUtils.isEmpty(profile.getBio())) {
            etBio.setText(profile.getBio());
        }
        if (!TextUtils.isEmpty(profile.getGender())) {
            actvGender.setText(profile.getGender(), false);
        }
        if (!TextUtils.isEmpty(profile.getDateOfBirth())) {
            etDob.setText(profile.getDateOfBirth());
        }
        if (!TextUtils.isEmpty(profile.getLocation())) {
            etLocation.setText(profile.getLocation());
        }

        if (!TextUtils.isEmpty(profile.getAvatarUrl())) {
            AvatarLoader.loadAvatar(this, profile.getAvatarUrl(), ivEditAvatar);
            updatePhotoActionButtons(true);
        } else {
            updatePhotoActionButtons(false);
        }
    }

    private void validateAndSaveProfile() {
        String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String bio = etBio.getText() != null ? etBio.getText().toString().trim() : "";
        String gender = actvGender.getText() != null ? actvGender.getText().toString().trim() : "";
        String dob = etDob.getText() != null ? etDob.getText().toString().trim() : "";
        String location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";

        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full Name is required");
            etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }

        if (!isUsernameValid) {
            Toast.makeText(this, "Please fix username errors before saving", Toast.LENGTH_SHORT).show();
            etUsername.requestFocus();
            return;
        }

        showLoadingOverlay("Saving profile changes…");

        if (isPhotoChanged && pendingAvatarBitmap != null) {
            uploadPhotoAndSave(fullName, username, bio, gender, dob, location);
        } else {
            String avatarUrl = isPhotoRemoved ? null : (currentProfile != null ? currentProfile.getAvatarUrl() : null);
            saveProfileMetadata(fullName, username, bio, gender, dob, location, avatarUrl);
        }
    }

    private void uploadPhotoAndSave(String fullName, String username, String bio, String gender, String dob, String location) {
        tvLoadingText.setText("Uploading profile photo…");

        UserRepository.getInstance().uploadAvatarViaProxy(pendingAvatarBitmap, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profileWithNewAvatar) {
                String newAvatarUrl = profileWithNewAvatar != null ? profileWithNewAvatar.getAvatarUrl() : null;
                saveProfileMetadata(fullName, username, bio, gender, dob, location, newAvatarUrl);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Photo upload error", e);
                Toast.makeText(EditProfileActivity.this, "Photo upload failed, saving text details...", Toast.LENGTH_LONG).show();
                String fallbackUrl = currentProfile != null ? currentProfile.getAvatarUrl() : null;
                saveProfileMetadata(fullName, username, bio, gender, dob, location, fallbackUrl);
            }
        });
    }

    private void saveProfileMetadata(String fullName, String username, String bio, String gender, String dob, String location, String avatarUrl) {
        tvLoadingText.setText("Updating Firestore profile…");

        UserProfile profileToUpdate = new UserProfile();
        profileToUpdate.setDisplayName(fullName);
        profileToUpdate.setUsername(username);
        profileToUpdate.setBio(bio);
        profileToUpdate.setGender(gender);
        profileToUpdate.setDateOfBirth(dob);
        profileToUpdate.setLocation(location);
        profileToUpdate.setAvatarUrl(avatarUrl);

        UserRepository.getInstance().updateMyProfile(profileToUpdate, new UserRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                hideLoadingOverlay();
                UserRepository.getInstance().invalidateCache();
                Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                hideLoadingOverlay();
                Log.e(TAG, "Failed to save profile changes", e);
                Toast.makeText(EditProfileActivity.this, "Failed to save changes: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoadingOverlay(String message) {
        if (tvLoadingText != null && message != null) {
            tvLoadingText.setText(message);
        }
        if (layoutLoadingOverlay != null) {
            layoutLoadingOverlay.setVisibility(View.VISIBLE);
        }
        btnSaveTop.setEnabled(false);
        btnSaveChanges.setEnabled(false);
    }

    private void hideLoadingOverlay() {
        if (layoutLoadingOverlay != null) {
            layoutLoadingOverlay.setVisibility(View.GONE);
        }
        btnSaveTop.setEnabled(isUsernameValid);
        btnSaveChanges.setEnabled(isUsernameValid);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (checkUsernameRunnable != null) {
            debounceHandler.removeCallbacks(checkUsernameRunnable);
        }
    }
}
