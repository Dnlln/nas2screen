package com.nas2screen.screensaver.slideshow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 24 hour clock in the bottom right corner.
 *
 * <p>The text slowly drifts inside a +/-20 px box (two incommensurate periods,
 * about five and seven minutes) so it never burns into the panel.
 */
public final class ClockView extends View {

    private static final long TICK_MS = 1000L;
    private static final long PERIOD_X_MS = 300_000L;   // 5 min
    private static final long PERIOD_Y_MS = 420_000L;   // 7 min

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.ROOT);
    private final RectF box = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (isEnabled() && isShown()) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private float driftAmplitude;
    private float cornerRadius;

    public ClockView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        float density = getResources().getDisplayMetrics().density;
        driftAmplitude = Math.max(20f, TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 14f, getResources().getDisplayMetrics()));

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(density * 3f, 0f, density * 1.5f, 0xB0000000);

        backgroundPaint.setColor(0x4D000000);
        cornerRadius = density * 8f;
    }

    public void setClockVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
        handler.removeCallbacks(tick);
        if (visible) {
            handler.post(tick);
        }
    }

    public void stopTicking() {
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            handler.post(tick);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // ~4.6% of the screen height: clearly readable on a 4K panel from the sofa
        textPaint.setTextSize(Math.max(28f, height * 0.046f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        String now = format.format(new Date());
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textWidth = textPaint.measureText(now);
        float textHeight = metrics.descent - metrics.ascent;

        float margin = width * 0.035f;
        float baseRight = width - margin;
        float baseBottom = height - margin;
        float baseline = baseBottom - metrics.descent;

        long time = System.currentTimeMillis();
        float dx = driftAmplitude * (float) Math.sin(2 * Math.PI * (time % PERIOD_X_MS) / PERIOD_X_MS);
        float dy = driftAmplitude * (float) Math.cos(2 * Math.PI * (time % PERIOD_Y_MS) / PERIOD_Y_MS);

        canvas.save();
        canvas.translate(dx, dy);

        float padX = textHeight * 0.45f;
        float padY = textHeight * 0.22f;
        box.set(baseRight - textWidth - padX,
                baseline + metrics.ascent - padY,
                baseRight + padX,
                baseline + metrics.descent + padY);
        canvas.drawRoundRect(box, cornerRadius, cornerRadius, backgroundPaint);
        canvas.drawText(now, baseRight - textWidth, baseline, textPaint);

        canvas.restore();
    }
}
