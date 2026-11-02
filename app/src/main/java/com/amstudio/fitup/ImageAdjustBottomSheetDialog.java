package com.amstudio.fitup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ImageAdjustBottomSheetDialog extends BottomSheetDialogFragment {

    public interface OnImageAdjustListener {
        void onAdjustValuesChanged(float brightness, float contrast, float saturation, boolean showGrid);
        void onDone(float brightness, float contrast, float saturation, boolean showGrid);
        void onCancel();
    }

    private enum ActiveTool {
        BRIGHTNESS, CONTRAST, SATURATION
    }

    private TextView tvActiveToolLabel;
    private TextView tvActiveToolValue;
    private SeekBar sbAdjustValue;

    private View btnAdjustGrid;
    private View btnAdjustLux;
    private View btnAdjustBrightness;
    private View btnAdjustContrast;
    private View btnAdjustSaturation;

    private TextView btnCancel;
    private TextView btnDone;

    private OnImageAdjustListener listener;

    private float brightness = 0f; // -100 to +100
    private float contrast = 1.0f; // 0.5 to 1.5
    private float saturation = 1.0f; // 0.0 to 2.0
    private boolean showGrid = false;

    private ActiveTool currentTool = ActiveTool.BRIGHTNESS;

    public static ImageAdjustBottomSheetDialog newInstance(float initBrightness, float initContrast,
                                                           float initSaturation, boolean initGrid) {
        ImageAdjustBottomSheetDialog dialog = new ImageAdjustBottomSheetDialog();
        Bundle args = new Bundle();
        args.putFloat("arg_brightness", initBrightness);
        args.putFloat("arg_contrast", initContrast);
        args.putFloat("arg_saturation", initSaturation);
        args.putBoolean("arg_grid", initGrid);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnImageAdjustListener(OnImageAdjustListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, 0);

        if (getArguments() != null) {
            brightness = getArguments().getFloat("arg_brightness", 0f);
            contrast = getArguments().getFloat("arg_contrast", 1.0f);
            saturation = getArguments().getFloat("arg_saturation", 1.0f);
            showGrid = getArguments().getBoolean("arg_grid", false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_image_adjust_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupClickListeners();
        setupSeekBar();
        updateToolUi();
    }

    private void initViews(View view) {
        tvActiveToolLabel = view.findViewById(R.id.tv_active_tool_label);
        tvActiveToolValue = view.findViewById(R.id.tv_active_tool_value);
        sbAdjustValue = view.findViewById(R.id.sb_adjust_value);

        btnAdjustGrid = view.findViewById(R.id.btn_adjust_grid);
        btnAdjustLux = view.findViewById(R.id.btn_adjust_lux);
        btnAdjustBrightness = view.findViewById(R.id.btn_adjust_brightness);
        btnAdjustContrast = view.findViewById(R.id.btn_adjust_contrast);
        btnAdjustSaturation = view.findViewById(R.id.btn_adjust_saturation);

        btnCancel = view.findViewById(R.id.btn_cancel_adjust);
        btnDone = view.findViewById(R.id.btn_done_adjust);
    }

    private void setupClickListeners() {
        if (btnAdjustGrid != null) {
            btnAdjustGrid.setOnClickListener(v -> {
                showGrid = !showGrid;
                notifyChanges();
            });
        }

        if (btnAdjustLux != null) {
            btnAdjustLux.setOnClickListener(v -> {
                brightness = 15f;
                contrast = 1.25f;
                saturation = 1.15f;
                updateToolUi();
                notifyChanges();
            });
        }

        if (btnAdjustBrightness != null) {
            btnAdjustBrightness.setOnClickListener(v -> {
                currentTool = ActiveTool.BRIGHTNESS;
                updateToolUi();
            });
        }

        if (btnAdjustContrast != null) {
            btnAdjustContrast.setOnClickListener(v -> {
                currentTool = ActiveTool.CONTRAST;
                updateToolUi();
            });
        }

        if (btnAdjustSaturation != null) {
            btnAdjustSaturation.setOnClickListener(v -> {
                currentTool = ActiveTool.SATURATION;
                updateToolUi();
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onCancel();
            });
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onDone(brightness, contrast, saturation, showGrid);
            });
        }
    }

    private void setupSeekBar() {
        if (sbAdjustValue == null) return;

        sbAdjustValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;

                switch (currentTool) {
                    case BRIGHTNESS:
                        brightness = progress - 100; // range -100 to +100
                        if (tvActiveToolValue != null) tvActiveToolValue.setText(String.valueOf((int) brightness));
                        break;
                    case CONTRAST:
                        contrast = 0.5f + (progress / 100f); // range 0.5 to 1.5
                        if (tvActiveToolValue != null) tvActiveToolValue.setText(String.format(java.util.Locale.US, "%.1f", contrast));
                        break;
                    case SATURATION:
                        saturation = progress / 100f; // range 0.0 to 2.0
                        if (tvActiveToolValue != null) tvActiveToolValue.setText(String.format(java.util.Locale.US, "%.1f", saturation));
                        break;
                }

                notifyChanges();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateToolUi() {
        if (sbAdjustValue == null || tvActiveToolLabel == null) return;

        switch (currentTool) {
            case BRIGHTNESS:
                tvActiveToolLabel.setText("Brightness");
                sbAdjustValue.setMax(200);
                sbAdjustValue.setProgress((int) (brightness + 100));
                if (tvActiveToolValue != null) tvActiveToolValue.setText(String.valueOf((int) brightness));
                break;
            case CONTRAST:
                tvActiveToolLabel.setText("Contrast");
                sbAdjustValue.setMax(100);
                sbAdjustValue.setProgress((int) ((contrast - 0.5f) * 100));
                if (tvActiveToolValue != null) tvActiveToolValue.setText(String.format(java.util.Locale.US, "%.1f", contrast));
                break;
            case SATURATION:
                tvActiveToolLabel.setText("Saturation");
                sbAdjustValue.setMax(200);
                sbAdjustValue.setProgress((int) (saturation * 100));
                if (tvActiveToolValue != null) tvActiveToolValue.setText(String.format(java.util.Locale.US, "%.1f", saturation));
                break;
        }
    }

    private void notifyChanges() {
        if (listener != null) {
            listener.onAdjustValuesChanged(brightness, contrast, saturation, showGrid);
        }
    }
}
