package com.amstudio.fitbond;

import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.amstudio.fitbond.helper.OneSignalManager;

import com.amstudio.fitbond.model.UserProfile;
import com.amstudio.fitbond.repository.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class AuthActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_MODE = "extra_initial_mode";
    public static final String MODE_LOGIN = "mode_login";
    public static final String MODE_SIGNUP = "mode_signup";

    private FirebaseAuth mAuth;
    private boolean isSignUpMode = false;

    // View References
    private ImageView btnBack;
    private TextView tabLogin;
    private TextView tabSignup;

    private TextInputLayout tilFullName;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirmPassword;

    private TextInputEditText etFullName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;

    private TextView tvForgotPassword;
    private TextView tvTermsNotice;
    private MaterialButton btnPrimaryAction;
    private LinearLayout btnGoogleSignin;
    private TextView tvBottomSwitchPrompt;
    private FrameLayout loadingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Check if user is already logged in
        if (mAuth.getCurrentUser() != null) {
            navigateToMainApp();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        // Ensure status bar & navigation bar icons remain light (white) over dark blue gradient
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        // Apply WindowInsets padding to avoid system bar, notch, and navigation bar overlap
        View rootLayout = findViewById(R.id.auth_root);
        View contentContainer = findViewById(R.id.auth_content_container);

        if (rootLayout != null && contentContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                contentContainer.setPadding(
                        contentContainer.getPaddingStart(),
                        systemBars.top + 12,
                        contentContainer.getPaddingEnd(),
                        systemBars.bottom + 20
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        initializeViews();
        setupClickListeners();
        setupTermsAndPrivacyNotice();

        // Determine starting mode from Intent
        Intent intent = getIntent();
        if (intent != null && MODE_SIGNUP.equals(intent.getStringExtra(EXTRA_INITIAL_MODE))) {
            setAuthMode(true);
        } else {
            setAuthMode(false);
        }
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btn_back);
        tabLogin = findViewById(R.id.tab_login);
        tabSignup = findViewById(R.id.tab_signup);

        tilFullName = findViewById(R.id.til_full_name);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);

        etFullName = findViewById(R.id.et_full_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);

        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        tvTermsNotice = findViewById(R.id.tv_terms_notice);
        btnPrimaryAction = findViewById(R.id.btn_primary_action);
        btnGoogleSignin = findViewById(R.id.btn_google_signin);
        tvBottomSwitchPrompt = findViewById(R.id.tv_bottom_switch_prompt);
        loadingOverlay = findViewById(R.id.loading_overlay);
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        if (tabLogin != null) {
            tabLogin.setOnClickListener(v -> setAuthMode(false));
        }

        if (tabSignup != null) {
            tabSignup.setOnClickListener(v -> setAuthMode(true));
        }

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
        }

        if (btnPrimaryAction != null) {
            btnPrimaryAction.setOnClickListener(v -> handlePrimaryAction());
        }

        if (btnGoogleSignin != null) {
            btnGoogleSignin.setOnClickListener(v -> {
                Toast.makeText(AuthActivity.this, "Google Sign-In selected", Toast.LENGTH_SHORT).show();
            });
        }

        if (tvBottomSwitchPrompt != null) {
            tvBottomSwitchPrompt.setOnClickListener(v -> setAuthMode(!isSignUpMode));
        }
    }

    private void setAuthMode(boolean signUpMode) {
        this.isSignUpMode = signUpMode;

        clearInputErrors();

        int selectedTextColor = ContextCompat.getColor(this, R.color.text_primary_white);
        int unselectedTextColor = ContextCompat.getColor(this, R.color.text_secondary_blue);

        if (signUpMode) {
            tabLogin.setBackground(null);
            tabLogin.setTextColor(unselectedTextColor);

            tabSignup.setBackgroundResource(R.drawable.bg_segmented_tab_selected);
            tabSignup.setTextColor(selectedTextColor);

            tilFullName.setVisibility(View.VISIBLE);
            tilConfirmPassword.setVisibility(View.VISIBLE);
            tvForgotPassword.setVisibility(View.GONE);
            tvTermsNotice.setVisibility(View.VISIBLE);

            btnPrimaryAction.setText(R.string.btn_create_account);

            setupBottomSwitchPrompt("Already have an account? ", "Login");

        } else {
            tabLogin.setBackgroundResource(R.drawable.bg_segmented_tab_selected);
            tabLogin.setTextColor(selectedTextColor);

            tabSignup.setBackground(null);
            tabSignup.setTextColor(unselectedTextColor);

            tilFullName.setVisibility(View.GONE);
            tilConfirmPassword.setVisibility(View.GONE);
            tvForgotPassword.setVisibility(View.VISIBLE);
            tvTermsNotice.setVisibility(View.GONE);

            btnPrimaryAction.setText(R.string.tab_login);

            setupBottomSwitchPrompt("Don't have an account? ", "Sign Up");
        }
    }

    private void setupBottomSwitchPrompt(String prefix, String actionText) {
        String fullText = prefix + actionText;
        SpannableStringBuilder spannable = new SpannableStringBuilder(fullText);

        int accentColor = ContextCompat.getColor(this, R.color.splash_electric_accent);
        int startIndex = fullText.indexOf(actionText);

        if (startIndex != -1) {
            spannable.setSpan(
                    new ForegroundColorSpan(accentColor),
                    startIndex,
                    startIndex + actionText.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        tvBottomSwitchPrompt.setText(spannable);
    }

    private void setupTermsAndPrivacyNotice() {
        if (tvTermsNotice == null) return;

        String fullText = getString(R.string.terms_privacy_notice);
        String termsText = "Terms of Service";
        String privacyText = "Privacy Policy";

        SpannableStringBuilder spannable = new SpannableStringBuilder(fullText);
        int accentColor = ContextCompat.getColor(this, R.color.splash_electric_accent);

        int termsStart = fullText.indexOf(termsText);
        if (termsStart != -1) {
            ClickableSpan termsSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Toast.makeText(AuthActivity.this, "Terms of Service clicked", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(accentColor);
                    ds.setUnderlineText(true);
                }
            };
            spannable.setSpan(termsSpan, termsStart, termsStart + termsText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int privacyStart = fullText.indexOf(privacyText);
        if (privacyStart != -1) {
            ClickableSpan privacySpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Toast.makeText(AuthActivity.this, "Privacy Policy clicked", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(accentColor);
                    ds.setUnderlineText(true);
                }
            };
            spannable.setSpan(privacySpan, privacyStart, privacyStart + privacyText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        tvTermsNotice.setText(spannable);
        tvTermsNotice.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void clearInputErrors() {
        tilFullName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        btnPrimaryAction.setEnabled(!show);
    }

    private void handleForgotPassword() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address first");
            return;
        }

        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(AuthActivity.this, "Password reset email sent to " + email, Toast.LENGTH_LONG).show();
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getLocalizedMessage() : "Error sending reset email";
                        Toast.makeText(AuthActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handlePrimaryAction() {
        clearInputErrors();

        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        boolean isValid = true;

        if (isSignUpMode) {
            String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
            String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

            if (TextUtils.isEmpty(fullName)) {
                tilFullName.setError("Full name is required");
                isValid = false;
            }

            if (TextUtils.isEmpty(email)) {
                tilEmail.setError("Email address is required");
                isValid = false;
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter a valid email address");
                isValid = false;
            }

            if (TextUtils.isEmpty(password)) {
                tilPassword.setError("Password is required");
                isValid = false;
            } else if (password.length() < 6) {
                tilPassword.setError("Password must be at least 6 characters");
                isValid = false;
            }

            if (TextUtils.isEmpty(confirmPassword)) {
                tilConfirmPassword.setError("Please confirm your password");
                isValid = false;
            } else if (!password.equals(confirmPassword)) {
                tilConfirmPassword.setError("Passwords do not match");
                isValid = false;
            }

            if (isValid) {
                showLoading(true);
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                            .setDisplayName(fullName)
                                            .build();
                                    user.updateProfile(profileUpdates);

                                    // Create Firestore UserProfile with unique username
                                    String rawUsername = email.contains("@") ? email.split("@")[0] : fullName.replace(" ", "_");
                                    UserRepository.getInstance().createProfileForNewUser(
                                            user.getUid(),
                                            email,
                                            fullName,
                                            rawUsername,
                                            new UserRepository.ProfileCallback() {
                                                @Override
                                                public void onSuccess(UserProfile profile) {
                                                    showLoading(false);
                                                    Toast.makeText(AuthActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                                                    navigateToMainApp();
                                                }

                                                @Override
                                                public void onError(Exception e) {
                                                    showLoading(false);
                                                    Toast.makeText(AuthActivity.this, "Account created, profile sync warning: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                                                    navigateToMainApp();
                                                }
                                            }
                                    );
                                } else {
                                    showLoading(false);
                                    navigateToMainApp();
                                }
                            } else {
                                showLoading(false);
                                String errorMsg = task.getException() != null ? task.getException().getLocalizedMessage() : "Registration failed";
                                Toast.makeText(AuthActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
            }

        } else {
            // Login Mode
            if (TextUtils.isEmpty(email)) {
                tilEmail.setError("Email address is required");
                isValid = false;
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter a valid email address");
                isValid = false;
            }

            if (TextUtils.isEmpty(password)) {
                tilPassword.setError("Password is required");
                isValid = false;
            }

            if (isValid) {
                showLoading(true);
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            showLoading(false);
                            if (task.isSuccessful()) {
                                Toast.makeText(AuthActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                                navigateToMainApp();
                            } else {
                                String errorMsg = task.getException() != null ? task.getException().getLocalizedMessage() : "Authentication failed";
                                Toast.makeText(AuthActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        }
    }

    private void navigateToMainApp() {
        if (mAuth.getCurrentUser() != null) {
            OneSignalManager.getInstance().login(mAuth.getCurrentUser().getUid());
        }
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
