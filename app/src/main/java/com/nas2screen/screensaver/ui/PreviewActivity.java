package com.nas2screen.screensaver.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.nas2screen.screensaver.R;
import com.nas2screen.screensaver.slideshow.ScreenView;
import com.nas2screen.screensaver.slideshow.SlideshowEngine;

/** Manual run of the slideshow: same engine as the dream, with MENU opening the settings. */
public final class PreviewActivity extends Activity {

    private ScreenView screenView;
    private SlideshowEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        screenView = new ScreenView(this);
        root.addView(screenView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TextView hint = new TextView(this);
        hint.setText(R.string.preview_hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        hint.setTextColor(0xCCFFFFFF);
        hint.setPadding(dp(16), dp(10), dp(16), dp(10));
        hint.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        hintParams.leftMargin = dp(24);
        hintParams.topMargin = dp(24);
        root.addView(hint, hintParams);

        setContentView(root);
        hideSystemUi();
        hint.postDelayed(() -> hint.animate().alpha(0f).setDuration(600).start(), 4000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine = new SlideshowEngine(this, screenView);
        engine.start();
    }

    @Override
    protected void onStop() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void hideSystemUi() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
