package com.amstudio.fitup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Custom animated focus ring view shown on tap-to-focus.
 */
public class FocusRingView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FocusRingView(Context context) {
        super(context);
        init();
    }

    public FocusRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FocusRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setColor(Color.parseColor("#38B6FF")); // Electric Blue
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 4f;

        canvas.drawCircle(cx, cy, radius, ringPaint);
    }

    /**
     * Show animated focus ring at (x, y) relative to parent view.
     */
    public void showFocusAt(float x, float y) {
        int width = getLayoutParams() != null ? getLayoutParams().width : 120;
        int height = getLayoutParams() != null ? getLayoutParams().height : 120;
        if (width <= 0) width = 120;
        if (height <= 0) height = 120;

        setX(x - width / 2f);
        setY(y - height / 2f);

        setScaleX(1.5f);
        setScaleY(1.5f);
        setAlpha(1.0f);
        setVisibility(View.VISIBLE);

        animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(250)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        postDelayed(() -> {
                            animate()
                                    .alpha(0.0f)
                                    .setDuration(300)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator anim) {
                                            setVisibility(View.GONE);
                                        }
                                    })
                                    .start();
                        }, 800);
                    }
                })
                .start();
    }
}
