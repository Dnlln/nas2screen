package com.nas2screen.screensaver;

import android.service.dreams.DreamService;

import com.nas2screen.screensaver.slideshow.ScreenView;
import com.nas2screen.screensaver.slideshow.SlideshowEngine;

/**
 * The system dream (screensaver) of the TV. Chosen in the TV settings under
 * "Screensaver"; started by the system after the configured idle time.
 */
public class PhotoDreamService extends DreamService {

    private ScreenView screenView;
    private SlideshowEngine engine;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        screenView = new ScreenView(this);
        setContentView(screenView);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (screenView == null) {
            return;
        }
        engine = new SlideshowEngine(this, screenView);
        engine.start();
        Diag.i("Dream", "dreaming started");
    }

    @Override
    public void onDreamingStopped() {
        stopEngine();
        super.onDreamingStopped();
        Diag.i("Dream", "dreaming stopped");
    }

    @Override
    public void onDetachedFromWindow() {
        stopEngine();
        super.onDetachedFromWindow();
    }

    private void stopEngine() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
    }
}
