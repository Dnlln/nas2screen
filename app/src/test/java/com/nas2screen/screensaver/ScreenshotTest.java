package com.nas2screen.screensaver;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.View.MeasureSpec;

import com.nas2screen.screensaver.slideshow.ScreenView;
import com.nas2screen.screensaver.ui.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Renders the real screens into PNG files so the layout can be reviewed without a
 * TV attached. Uses Robolectric's native graphics mode (real Skia rendering).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 28, qualifiers = "w1920dp-h1080dp-mdpi")
public class ScreenshotTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    @Test
    public void settingsScreenSaf() throws Exception {
        Prefs prefs = Prefs.get(RuntimeEnvironment.getApplication());
        prefs.setSourceType(Prefs.SOURCE_LOCAL);
        prefs.setLocalPath("/storage/emulated/0/DCIM/Camera",
                "Internal storage / DCIM/Camera");
        prefs.setIntervalSeconds(15);

        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class)
                .create().start().resume().visible().get();
        View decor = activity.getWindow().getDecorView();
        write(draw(decor), "screenshot-settings.png");
        activity.finish();
    }

    @Test
    public void settingsScreenSmb() throws Exception {
        Prefs prefs = Prefs.get(RuntimeEnvironment.getApplication());
        prefs.setSourceType(Prefs.SOURCE_SMB);
        prefs.setSmb("192.168.88.23", "Multimedia\\Camera Uploads\\LMR-LX9\\2026\\02",
                "nasuser", "secret");
        prefs.setIntervalSeconds(15);

        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class)
                .create().start().resume().visible().get();
        View decor = activity.getWindow().getDecorView();
        write(draw(decor), "screenshot-settings-smb.png");
        activity.finish();
    }

    @Test
    public void slideshowScreen() throws Exception {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        ScreenView view = new ScreenView(host);

        Bitmap photo = fakePhoto(1600, 1200);
        Bitmap background = fakePhoto(240, 180);
        view.setClockVisible(true);
        view.showFrame(photo, background, false);

        Bitmap bitmap = draw(view);
        write(bitmap, "screenshot-slideshow.png");

        view.showMessage("Источник недоступен. Проверьте настройки.",
                "Неверное имя пользователя или пароль\nПовтор через 30 с");
        Bitmap messageScreen = draw(view);
        write(messageScreen, "screenshot-error.png");
        view.clear();
    }

    private Bitmap draw(View view) {
        view.measure(MeasureSpec.makeMeasureSpec(WIDTH, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(HEIGHT, MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        view.draw(canvas);
        return bitmap;
    }

    /** Simple stand-in for a photo: sky gradient, sun and dark hills. */
    private Bitmap fakePhoto(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0xFF2F6F9E, 0xFF9CC3D9, 0xFFE8D9B8}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(null);
        paint.setColor(0xFFFFE08A);
        canvas.drawCircle(width * 0.72f, height * 0.28f, Math.min(width, height) * 0.09f, paint);

        paint.setColor(0xFF2E6B4F);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(0, height);
        path.lineTo(width * 0.22f, height * 0.52f);
        path.lineTo(width * 0.45f, height * 0.86f);
        path.lineTo(width * 0.66f, height * 0.46f);
        path.lineTo(width, height * 0.9f);
        path.lineTo(width, height);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFF17402F);
        android.graphics.Path near = new android.graphics.Path();
        near.moveTo(0, height);
        near.lineTo(width * 0.34f, height * 0.72f);
        near.lineTo(width * 0.62f, height * 0.98f);
        near.lineTo(width, height * 0.7f);
        near.lineTo(width, height);
        near.close();
        canvas.drawPath(near, paint);
        return bitmap;
    }

    private void write(Bitmap bitmap, String name) throws Exception {
        File docs = new File("../docs");
        //noinspection ResultOfMethodCallIgnored
        docs.mkdirs();
        File target = new File(docs, name);
        try (FileOutputStream out = new FileOutputStream(target)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        assertTrue(target.length() > 0);
    }
}
