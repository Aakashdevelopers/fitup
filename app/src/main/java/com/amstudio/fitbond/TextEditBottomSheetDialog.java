package com.amstudio.fitbond;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class TextEditBottomSheetDialog extends BottomSheetDialogFragment {

    public interface OnTextEditListener {
        void onTextChanged(String text, int textColor, boolean hasFillBackground, int alignment);
        void onDone(String text, int textColor, boolean hasFillBackground, int alignment);
    }

    private TextView btnDone;
    private View btnColorWheel;
    private View viewActiveColor;
    private TextView btnFill;
    private View btnAlign;
    private EditText etTextInput;

    private String currentText = "";
    private int currentColor = Color.WHITE;
    private boolean hasFillBackground = true;
    private int currentAlignment = Gravity.CENTER;

    private static final int[] COLORS = new int[]{
            Color.WHITE, Color.parseColor("#3897F0"), Color.parseColor("#FFCC00"),
            Color.parseColor("#FF3B30"), Color.parseColor("#34C759"), Color.BLACK
    };
    private int colorIndex = 0;

    private OnTextEditListener listener;

    public static TextEditBottomSheetDialog newInstance(String initText, int initColor, boolean initFill, int initAlign) {
        TextEditBottomSheetDialog dialog = new TextEditBottomSheetDialog();
        Bundle args = new Bundle();
        args.putString("arg_text", initText);
        args.putInt("arg_color", initColor);
        args.putBoolean("arg_fill", initFill);
        args.putInt("arg_align", initAlign);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnTextEditListener(OnTextEditListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, 0);

        if (getArguments() != null) {
            currentText = getArguments().getString("arg_text", "");
            currentColor = getArguments().getInt("arg_color", Color.WHITE);
            hasFillBackground = getArguments().getBoolean("arg_fill", true);
            currentAlignment = getArguments().getInt("arg_align", Gravity.CENTER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_text_edit_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupClickListeners();
        setupTextWatcher();
        updateFormattingUi();
    }

    private void initViews(View view) {
        btnDone = view.findViewById(R.id.btn_done_text);
        btnColorWheel = view.findViewById(R.id.btn_text_color);
        viewActiveColor = view.findViewById(R.id.view_active_color);
        btnFill = view.findViewById(R.id.btn_text_fill);
        btnAlign = view.findViewById(R.id.btn_text_align);
        etTextInput = view.findViewById(R.id.et_text_input);

        if (etTextInput != null && !TextUtils.isEmpty(currentText)) {
            etTextInput.setText(currentText);
            etTextInput.setSelection(currentText.length());
        }
    }

    private void setupClickListeners() {
        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onDone(currentText, currentColor, hasFillBackground, currentAlignment);
                }
            });
        }

        if (btnColorWheel != null) {
            btnColorWheel.setOnClickListener(v -> {
                colorIndex = (colorIndex + 1) % COLORS.length;
                currentColor = COLORS[colorIndex];
                updateFormattingUi();
                notifyChanges();
            });
        }

        if (btnFill != null) {
            btnFill.setOnClickListener(v -> {
                hasFillBackground = !hasFillBackground;
                updateFormattingUi();
                notifyChanges();
            });
        }

        if (btnAlign != null) {
            btnAlign.setOnClickListener(v -> {
                if (currentAlignment == Gravity.CENTER) {
                    currentAlignment = Gravity.LEFT;
                } else if (currentAlignment == Gravity.LEFT) {
                    currentAlignment = Gravity.RIGHT;
                } else {
                    currentAlignment = Gravity.CENTER;
                }
                updateFormattingUi();
                notifyChanges();
            });
        }
    }

    private void setupTextWatcher() {
        if (etTextInput == null) return;

        etTextInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentText = s != null ? s.toString() : "";
                notifyChanges();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateFormattingUi() {
        if (viewActiveColor != null) {
            viewActiveColor.setBackgroundColor(currentColor);
        }

        if (btnFill != null) {
            btnFill.setAlpha(hasFillBackground ? 1.0f : 0.4f);
        }

        if (etTextInput != null) {
            etTextInput.setTextColor(currentColor);
            etTextInput.setGravity(currentAlignment);
        }
    }

    private void notifyChanges() {
        if (listener != null) {
            listener.onTextChanged(currentText, currentColor, hasFillBackground, currentAlignment);
        }
    }
}
