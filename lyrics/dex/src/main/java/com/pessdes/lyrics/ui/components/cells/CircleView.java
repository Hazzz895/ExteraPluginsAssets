package com.pessdes.lyrics.ui.components.cells;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

public class CircleView extends View {
    private final Paint paint;

    public CircleView(Context context) {
        super(context);
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        int centerX = getWidth() / 2;
        int centerY = height / 2;

        int radius = Math.min(width, height) / 2;

        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }
}