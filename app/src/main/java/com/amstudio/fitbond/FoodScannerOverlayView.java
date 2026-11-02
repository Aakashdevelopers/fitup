package com.amstudio.fitbond;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Custom Animated Reticle View for AI Food Scanner.
 * Replicates scanner corner brackets with an animated horizontal laser line sweep.
 */
public class FoodScannerOverlayView extends View {

    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint laserGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path cornerPath = new Path();
    private final RectF arcRect = new RectF();
    private ValueAnimator laserAnimator;
    private float laserProgress = 0f; // 0.0f (top) to 1.0f (bottom)

    public FoodScannerOverlayView(Context context) {
        super(context);
        init();
    }

    public FoodScannerOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FoodScannerOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        // White corner brackets paint
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(5f * density); // ~5dp thick stroke
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setStrokeJoin(Paint.Join.ROUND);

        // Bright laser line paint
        laserPaint.setColor(Color.WHITE);
        laserPaint.setStyle(Paint.Style.STROKE);
        laserPaint.setStrokeWidth(3.5f * density);
        laserPaint.setStrokeCap(Paint.Cap.ROUND);

        // Laser glow paint
        laserGlowPaint.setStyle(Paint.Style.FILL);

        setupAnimator();
    }

    private void setupAnimator() {
        laserAnimator = ValueAnimator.ofFloat(0.06f, 0.94f);
        laserAnimator.setDuration(1600);
        laserAnimator.setRepeatCount(ValueAnimator.INFINITE);
        laserAnimator.setRepeatMode(ValueAnimator.REVERSE);
        laserAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        laserAnimator.addUpdateListener(animation -> {
            laserProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE && laserAnimator != null && !laserAnimator.isRunning()) {
            laserAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (laserAnimator != null) {
            laserAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (laserAnimator != null) {
            if (visibility == VISIBLE) {
                if (!laserAnimator.isRunning()) {
                    laserAnimator.start();
                }
            } else {
                laserAnimator.cancel();
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float padding = cornerPaint.getStrokeWidth() / 2f + 4f * density;
        float cornerLength = 46f * density; // Length of corner bracket arms
        float cornerRadius = 24f * density; // Radius of corner curves

        float left = padding;
        float top = padding;
        float right = width - padding;
        float bottom = height - padding;

        cornerPath.reset();

        // Top-Left Corner ⌜
        cornerPath.moveTo(left, top + cornerLength);
        cornerPath.lineTo(left, top + cornerRadius);
        arcRect.set(left, top, left + cornerRadius * 2, top + cornerRadius * 2);
        cornerPath.arcTo(arcRect, 180, 90, false);
        cornerPath.lineTo(left + cornerLength, top);

        // Top-Right Corner ⌝
        cornerPath.moveTo(right - cornerLength, top);
        cornerPath.lineTo(right - cornerRadius, top);
        arcRect.set(right - cornerRadius * 2, top, right, top + cornerRadius * 2);
        cornerPath.arcTo(arcRect, 270, 90, false);
        cornerPath.lineTo(right, top + cornerLength);

        // Bottom-Right Corner ⌟
        cornerPath.moveTo(right, bottom - cornerLength);
        cornerPath.lineTo(right, bottom - cornerRadius);
        arcRect.set(right - cornerRadius * 2, bottom - cornerRadius * 2, right, bottom);
        cornerPath.arcTo(arcRect, 0, 90, false);
        cornerPath.lineTo(right - cornerLength, bottom);

        // Bottom-Left Corner ⌞
        cornerPath.moveTo(left + cornerLength, bottom);
        cornerPath.lineTo(left + cornerRadius, bottom);
        arcRect.set(left, bottom - cornerRadius * 2, left + cornerRadius * 2, bottom);
        cornerPath.arcTo(arcRect, 90, 90, false);
        cornerPath.lineTo(left, bottom - cornerLength);

        // Draw the 4 rounded white corner brackets
        canvas.drawPath(cornerPath, cornerPaint);

        // Calculate laser line position
        float laserY = top + (bottom - top) * laserProgress;
        float lineMargin = 12f * density;
        float laserLeft = left + lineMargin;
        float laserRight = right - lineMargin;

        // Draw laser glow (semi-transparent subtle gradient above/below line)
        float glowHeight = 18f * density;
        LinearGradient glowShader = new LinearGradient(
                0, laserY - glowHeight,
                0, laserY + glowHeight,
                new int[]{0x00FFFFFF, 0x35FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        laserGlowPaint.setShader(glowShader);
        canvas.drawRect(laserLeft, laserY - glowHeight, laserRight, laserY + glowHeight, laserGlowPaint);

        // Draw solid bright horizontal laser line
        canvas.drawLine(laserLeft, laserY, laserRight, laserY, laserPaint);
    }
}
