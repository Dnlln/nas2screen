package com.nas2screen.screensaver.slideshow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The screen of the slideshow: blurred background, two cross fading photo layers,
 * a message panel and the clock.
 *
 * <p>Photos are shown with {@code FIT_CENTER}, so they keep their proportions and
 * are never cropped or stretched. The background is the same photo blurred at
 * 1/8 scale and stretched, so the letterbox bars are filled without extra memory.
 */
public final class ScreenView extends FrameLayout {

    private final ImageView[] mains = new ImageView[2];
    private final ImageView[] blurs = new ImageView[2];
    private final ClockView clock;
    private final LinearLayout messagePanel;
    private final TextView messageTitle;
    private final TextView messageText;

    private int front = 0;
    private int fading = -1;
    private int animationMs = 500;
    private Bitmap frontMain;
    private Bitmap frontBlur;
    private Bitmap fadingMain;
    private Bitmap fadingBlur;

    public ScreenView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);

        for (int i = 0; i < 2; i++) {
            ImageView blur = new ImageView(context);
            // the blurred copy is stretched over the whole screen (spec 6), it is blurred
            // anyway so the small distortion is invisible
            blur.setScaleType(ImageView.ScaleType.FIT_XY);
            blur.setAlpha(0f);
            blur.setFocusable(false);
            blur.setClickable(false);
            blurs[i] = blur;
            addView(blur, fill());
        }
        // darkens only the letterbox bars: the photos are added above the scrim
        View scrim = new View(context);
        scrim.setBackgroundColor(0x59000000);
        addView(scrim, fill());

        for (int i = 0; i < 2; i++) {
            ImageView main = new ImageView(context);
            main.setScaleType(ImageView.ScaleType.FIT_CENTER);
            main.setAlpha(0f);
            main.setFocusable(false);
            main.setClickable(false);
            mains[i] = main;
            addView(main, fill());
        }

        messagePanel = new LinearLayout(context);
        messagePanel.setOrientation(LinearLayout.VERTICAL);
        messagePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        messagePanel.setBackgroundResource(com.nas2screen.screensaver.R.drawable.bg_panel);
        int pad = dp(28);
        messagePanel.setPadding(pad, pad, pad, pad);
        messagePanel.setVisibility(GONE);

        messageTitle = new TextView(context);
        messageTitle.setTextColor(0xFFECEFF1);
        messageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        messageTitle.setGravity(Gravity.CENTER);
        messagePanel.addView(messageTitle, wrap());

        messageText = new TextView(context);
        messageText.setTextColor(0xFF95A2AC);
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        messageText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = wrap();
        textParams.topMargin = dp(10);
        messagePanel.addView(messageText, textParams);

        LayoutParams panelParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(messagePanel, panelParams);

        clock = new ClockView(context);
        addView(clock, fill());
    }

    public void setTransitionMs(int ms) {
        animationMs = Math.max(0, ms);
    }

    public void setClockVisible(boolean visible) {
        clock.setClockVisible(visible);
    }

    public void showFrame(Bitmap main, Bitmap blur, boolean animate) {
        completeFade();
        int incoming = 1 - front;

        mains[incoming].setImageBitmap(main);
        blurs[incoming].setImageBitmap(blur);

        boolean crossFade = animate && frontMain != null && animationMs > 0;
        if (crossFade) {
            mains[incoming].setAlpha(0f);
            blurs[incoming].setAlpha(0f);
            fading = front;
            fadingMain = frontMain;
            fadingBlur = frontBlur;

            mains[incoming].animate().alpha(1f).setDuration(animationMs)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(this::completeFade)
                    .start();
            blurs[incoming].animate().alpha(1f).setDuration(animationMs)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            mains[fading].animate().alpha(0f).setDuration(animationMs)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            blurs[fading].animate().alpha(0f).setDuration(animationMs)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        } else {
            mains[incoming].setAlpha(1f);
            blurs[incoming].setAlpha(1f);
            mains[front].animate().cancel();
            blurs[front].animate().cancel();
            mains[front].setImageDrawable(null);
            blurs[front].setImageDrawable(null);
            mains[front].setAlpha(0f);
            blurs[front].setAlpha(0f);
            recycle(frontMain);
            recycle(frontBlur);
        }

        frontMain = main;
        frontBlur = blur;
        front = incoming;
    }

    /** Finishes a running cross fade at once and frees the old bitmaps. */
    private void completeFade() {
        if (fading < 0) {
            return;
        }
        int index = fading;
        fading = -1;
        mains[index].animate().cancel();
        blurs[index].animate().cancel();
        mains[index].setImageDrawable(null);
        blurs[index].setImageDrawable(null);
        mains[index].setAlpha(0f);
        blurs[index].setAlpha(0f);
        recycle(fadingMain);
        recycle(fadingBlur);
        fadingMain = null;
        fadingBlur = null;
    }

    public void showMessage(CharSequence title, CharSequence text) {
        messageTitle.setText(title);
        if (text == null || text.length() == 0) {
            messageText.setText("");
            messageText.setVisibility(GONE);
        } else {
            messageText.setText(text);
            messageText.setVisibility(VISIBLE);
        }
        messagePanel.setVisibility(VISIBLE);
        messagePanel.bringToFront();
        clock.bringToFront();
    }

    public void hideMessage() {
        messagePanel.setVisibility(GONE);
    }

    /** Stops everything and frees all bitmaps. */
    public void clear() {
        mains[0].animate().cancel();
        mains[1].animate().cancel();
        blurs[0].animate().cancel();
        blurs[1].animate().cancel();
        completeFade();
        for (int i = 0; i < 2; i++) {
            mains[i].setImageDrawable(null);
            blurs[i].setImageDrawable(null);
            mains[i].setAlpha(0f);
            blurs[i].setAlpha(0f);
        }
        recycle(frontMain);
        recycle(frontBlur);
        frontMain = null;
        frontBlur = null;
        clock.stopTicking();
        hideMessage();
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private LayoutParams fill() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    /** Exposed for the preview screen: the photo layer is what matters for sizing. */
    public View photoLayer() {
        return mains[0];
    }
}
