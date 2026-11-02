package com.amstudio.fitbond;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class RatioBottomSheetDialog extends BottomSheetDialogFragment {

    public enum RatioMode {
        SQUARE, ORIGINAL, PORTRAIT, LANDSCAPE
    }

    public interface OnRatioSelectedListener {
        void onRatioChanged(RatioMode mode);
        void onDone(RatioMode selectedMode);
        void onCancel();
    }

    private View cardSquare;
    private View cardOriginal;
    private View cardPortrait;
    private View cardLandscape;

    private TextView btnCancel;
    private TextView btnDone;

    private RatioMode currentMode = RatioMode.SQUARE;
    private RatioMode initialMode = RatioMode.SQUARE;

    private OnRatioSelectedListener listener;

    public static RatioBottomSheetDialog newInstance(RatioMode initialMode) {
        RatioBottomSheetDialog dialog = new RatioBottomSheetDialog();
        Bundle args = new Bundle();
        args.putString("arg_mode", initialMode != null ? initialMode.name() : RatioMode.SQUARE.name());
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnRatioSelectedListener(OnRatioSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, 0);

        if (getArguments() != null) {
            String modeStr = getArguments().getString("arg_mode");
            if (modeStr != null) {
                try {
                    initialMode = RatioMode.valueOf(modeStr);
                    currentMode = initialMode;
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_image_ratio_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupClickListeners();
        updateSelectedCardUi();
    }

    private void initViews(View view) {
        cardSquare = view.findViewById(R.id.card_ratio_square);
        cardOriginal = view.findViewById(R.id.card_ratio_original);
        cardPortrait = view.findViewById(R.id.card_ratio_portrait);
        cardLandscape = view.findViewById(R.id.card_ratio_landscape);

        btnCancel = view.findViewById(R.id.btn_cancel_ratio);
        btnDone = view.findViewById(R.id.btn_done_ratio);
    }

    private void setupClickListeners() {
        if (cardSquare != null) {
            cardSquare.setOnClickListener(v -> selectMode(RatioMode.SQUARE));
        }

        if (cardOriginal != null) {
            cardOriginal.setOnClickListener(v -> selectMode(RatioMode.ORIGINAL));
        }

        if (cardPortrait != null) {
            cardPortrait.setOnClickListener(v -> selectMode(RatioMode.PORTRAIT));
        }

        if (cardLandscape != null) {
            cardLandscape.setOnClickListener(v -> selectMode(RatioMode.LANDSCAPE));
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onRatioChanged(initialMode);
                    listener.onCancel();
                }
            });
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onDone(currentMode);
                }
            });
        }
    }

    private void selectMode(RatioMode mode) {
        this.currentMode = mode;
        updateSelectedCardUi();
        if (listener != null) {
            listener.onRatioChanged(mode);
        }
    }

    private void updateSelectedCardUi() {
        if (cardSquare != null) cardSquare.setAlpha(currentMode == RatioMode.SQUARE ? 1.0f : 0.5f);
        if (cardOriginal != null) cardOriginal.setAlpha(currentMode == RatioMode.ORIGINAL ? 1.0f : 0.5f);
        if (cardPortrait != null) cardPortrait.setAlpha(currentMode == RatioMode.PORTRAIT ? 1.0f : 0.5f);
        if (cardLandscape != null) cardLandscape.setAlpha(currentMode == RatioMode.LANDSCAPE ? 1.0f : 0.5f);
    }
}
