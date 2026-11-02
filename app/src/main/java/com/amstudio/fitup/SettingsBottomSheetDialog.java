package com.amstudio.fitup;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;

public class SettingsBottomSheetDialog extends BottomSheetDialogFragment {

    private ImageButton btnClose;
    private View rowEditPersonal;
    private View rowShareProfile;
    private View rowPrivacy;
    private View rowNotifications;
    private View rowHelp;
    private View rowLogout;

    public interface OnEditProfileListener {
        void onOpenEditProfile();
    }

    private OnEditProfileListener editProfileListener;

    public static SettingsBottomSheetDialog newInstance() {
        return new SettingsBottomSheetDialog();
    }

    public void setOnEditProfileListener(OnEditProfileListener listener) {
        this.editProfileListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupClickListeners();
    }

    private void initViews(View view) {
        btnClose = view.findViewById(R.id.btn_close_settings);
        rowEditPersonal = view.findViewById(R.id.row_edit_personal_details);
        rowShareProfile = view.findViewById(R.id.row_share_profile);
        rowPrivacy = view.findViewById(R.id.row_privacy_security);
        rowNotifications = view.findViewById(R.id.row_notifications);
        rowHelp = view.findViewById(R.id.row_help_support);
        rowLogout = view.findViewById(R.id.row_logout);
    }

    private void setupClickListeners() {
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        if (rowEditPersonal != null) {
            rowEditPersonal.setOnClickListener(v -> {
                dismiss();
                if (editProfileListener != null) {
                    editProfileListener.onOpenEditProfile();
                } else if (getContext() != null) {
                    Intent intent = new Intent(getContext(), EditProfileActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (rowShareProfile != null) {
            rowShareProfile.setOnClickListener(v -> showToast("Profile link copied to clipboard!"));
        }

        if (rowPrivacy != null) {
            rowPrivacy.setOnClickListener(v -> showToast("Privacy & Security Settings"));
        }

        if (rowNotifications != null) {
            rowNotifications.setOnClickListener(v -> showToast("Notification Preferences"));
        }

        if (rowHelp != null) {
            rowHelp.setOnClickListener(v -> showToast("Help & Support Center"));
        }

        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
        }
    }

    private void showLogoutConfirmationDialog() {
        Context context = getContext();
        if (context == null) return;

        new AlertDialog.Builder(context)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of FitUp?")
                .setPositiveButton("Log Out", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(context, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
