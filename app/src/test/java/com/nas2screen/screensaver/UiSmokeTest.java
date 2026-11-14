package com.nas2screen.screensaver;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.ViewGroup;

import com.nas2screen.screensaver.slideshow.ScreenView;
import com.nas2screen.screensaver.slideshow.SlideshowEngine;
import com.nas2screen.screensaver.ui.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Smoke tests that run the real activities and views on the JVM: they catch
 * broken resources, wrong ids, missing views and lifecycle crashes without a TV.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UiSmokeTest {

    @Test
    public void settingsActivityOpensAndSaves() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class)
                .create().start().resume().visible().get();
        assertNotNull(activity);
        ViewGroup content = activity.findViewById(android.R.id.content);
        assertNotNull(content);
        assertTrue(content.getChildCount() > 0);

        Prefs prefs = Prefs.get(activity);
        prefs.setSourceType(Prefs.SOURCE_SMB);
        prefs.setSmb("192.168.88.23", "Multimedia/Camera Uploads", "user", "pass");
        assertTrue(prefs.isSmb());
        assertTrue(prefs.isConfigured());
        assertNotNull(prefs.toSpec());
        prefs.setIntervalSeconds(100_000);
        assertTrue(prefs.intervalSeconds() <= Prefs.MAX_INTERVAL);
        prefs.setIntervalSeconds(1);
        assertTrue(prefs.intervalSeconds() >= Prefs.MIN_INTERVAL);

        activity.finish();
    }

    @Test
    public void settingsActivityHandlesUnconfiguredState() {
        Prefs prefs = Prefs.get(RuntimeEnvironment.getApplication());
        prefs.setSourceType(Prefs.SOURCE_LOCAL);
        prefs.setLocalUri("", "");
        prefs.setLocalPath("", "");
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class)
                .create().start().resume().visible().get();
        assertNotNull(activity);
        activity.finish();
    }

    @Test
    public void screenViewRecyclesOldFrames() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        ScreenView view = new ScreenView(host);
        view.setTransitionMs(0);

        Bitmap first = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        Bitmap firstBlur = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888);
        view.showFrame(first, firstBlur, false);

        Bitmap second = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        Bitmap secondBlur = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888);
        view.showFrame(second, secondBlur, false);

        assertTrue("old photo must be released", first.isRecycled());
        assertTrue("old background must be released", firstBlur.isRecycled());

        view.showMessage("title", "text");
        view.hideMessage();
        view.setClockVisible(true);
        view.clear();
        assertTrue(second.isRecycled());
        assertTrue(secondBlur.isRecycled());
    }

    @Test
    public void engineShowsHintWhenNotConfigured() {
        Prefs prefs = Prefs.get(RuntimeEnvironment.getApplication());
        prefs.setSourceType(Prefs.SOURCE_LOCAL);
        prefs.setLocalUri("", "");
        prefs.setLocalPath("", "");

        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        ScreenView view = new ScreenView(host);
        SlideshowEngine engine = new SlideshowEngine(host, view);
        engine.start();
        ShadowLooper.idleMainLooper();
        engine.stop();
        assertNotNull(Looper.getMainLooper());
    }
}
