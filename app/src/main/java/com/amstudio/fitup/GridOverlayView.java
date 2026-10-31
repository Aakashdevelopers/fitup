package com.amstudio.fitup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Custom View for drawing a subtle 3x3 composition grid over the camera preview.
 */
public class GridOverlayView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GridOverlayView(Context context) {
        super(context);
        init();
    }

    public GridOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GridOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(Color.parseColor("#40FFFFFF")); // Low-opacity white
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        float thirdWidth = width / 3f;
        float thirdHeight = height / 3f;

        // Draw vertical lines
        canvas.drawLine(thirdWidth, 0, thirdWidth, height, gridPaint);
        canvas.drawLine(thirdWidth * 2, 0, thirdWidth * 2, height, gridPaint);

        // Draw horizontal lines
        canvas.drawLine(0, thirdHeight, width, thirdHeight, gridPaint);
        canvas.drawLine(0, thirdHeight * 2, width, thirdHeight * 2, gridPaint);
    }
}
