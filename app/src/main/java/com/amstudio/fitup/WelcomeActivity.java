package com.amstudio.fitup;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class WelcomeActivity extends AppCompatActivity {

    private MaterialButton btnGetStarted;
    private MaterialButton tvLoginAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is already logged in before setting up UI
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welcome);

        // Ensure status bar & navigation bar icons are light (white) over dark blue gradient
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        // Apply WindowInsets padding to avoid system bar, notch, and navigation bar overlap
        View rootScroll = findViewById(R.id.welcome_root_scroll);
        View contentContainer = findViewById(R.id.welcome_content_container);

        if (rootScroll != null && contentContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootScroll, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                contentContainer.setPadding(
                        contentContainer.getPaddingStart(),
                        systemBars.top + 16,
                        contentContainer.getPaddingEnd(),
                        systemBars.bottom + 20
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        btnGetStarted = findViewById(R.id.btn_get_started);
        tvLoginAction = findViewById(R.id.tv_login_action);

        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(v -> {
                Intent intent = new Intent(WelcomeActivity.this, AuthActivity.class);
                intent.putExtra(AuthActivity.EXTRA_INITIAL_MODE, AuthActivity.MODE_SIGNUP);
                startActivity(intent);
            });
        }

        if (tvLoginAction != null) {
            tvLoginAction.setOnClickListener(v -> {
                Intent intent = new Intent(WelcomeActivity.this, AuthActivity.class);
                intent.putExtra(AuthActivity.EXTRA_INITIAL_MODE, AuthActivity.MODE_LOGIN);
                startActivity(intent);
            });
        }
    }
}
