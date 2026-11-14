package com.nas2screen.screensaver.image;

import android.graphics.Bitmap;

/**
 * Tiny, allocation friendly blur used for the letterbox background. Works on a
 * copy that is at most 1/8 of the screen, so it costs well under a megabyte.
 */
public final class Blur {

    private Blur() {
    }

    /**
     * Returns a small blurred copy of {@code source}: its longer side is at most
     * {@code maxLongSide} pixels. The caller shows it stretched to the screen, so
     * no full size bitmap is ever allocated for the background.
     */
    public static Bitmap background(Bitmap source, int maxLongSide) {
        if (source == null || source.getWidth() < 2 || source.getHeight() < 2) {
            return null;
        }
        int longSide = Math.max(source.getWidth(), source.getHeight());
        float scale = Math.min(1f, maxLongSide / (float) longSide);
        int width = Math.max(2, Math.round(source.getWidth() * scale));
        int height = Math.max(2, Math.round(source.getHeight() * scale));

        Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
        if (small == source) {
            small = source.copy(Bitmap.Config.ARGB_8888, true);
        }
        if (small == null) {
            return null;
        }
        if (small.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap converted = small.copy(Bitmap.Config.ARGB_8888, true);
            if (converted != null) {
                small.recycle();
                small = converted;
            }
        }
        int radius = Math.max(3, Math.min(width, height) / 10);
        blur(small, radius, 3);
        return small;
    }

    /** In place box blur, applied {@code passes} times for a smoother look. */
    public static void blur(Bitmap bitmap, int radius, int passes) {
        if (bitmap == null || radius < 1 || passes < 1) {
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width < 2 || height < 2) {
            return;
        }
        int[] source = new int[width * height];
        int[] temp = new int[width * height];
        bitmap.getPixels(source, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < passes; pass++) {
            blurHorizontal(source, temp, width, height, radius);
            blurVertical(temp, source, width, height, radius);
        }
        bitmap.setPixels(source, 0, width, 0, 0, width, height);
    }

    private static void blurHorizontal(int[] src, int[] dst, int width, int height, int radius) {
        int span = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int sumA = 0;
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;
            for (int k = -radius; k <= radius; k++) {
                int pixel = src[row + clamp(k, width)];
                sumA += (pixel >>> 24) & 0xff;
                sumR += (pixel >> 16) & 0xff;
                sumG += (pixel >> 8) & 0xff;
                sumB += pixel & 0xff;
            }
            for (int x = 0; x < width; x++) {
                dst[row + x] = ((sumA / span) << 24) | ((sumR / span) << 16)
                        | ((sumG / span) << 8) | (sumB / span);
                int add = src[row + clamp(x + radius + 1, width)];
                int sub = src[row + clamp(x - radius, width)];
                sumA += ((add >>> 24) & 0xff) - ((sub >>> 24) & 0xff);
                sumR += ((add >> 16) & 0xff) - ((sub >> 16) & 0xff);
                sumG += ((add >> 8) & 0xff) - ((sub >> 8) & 0xff);
                sumB += (add & 0xff) - (sub & 0xff);
            }
        }
    }

    private static void blurVertical(int[] src, int[] dst, int width, int height, int radius) {
        int span = radius * 2 + 1;
        for (int x = 0; x < width; x++) {
            int sumA = 0;
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;
            for (int k = -radius; k <= radius; k++) {
                int pixel = src[clamp(k, height) * width + x];
                sumA += (pixel >>> 24) & 0xff;
                sumR += (pixel >> 16) & 0xff;
                sumG += (pixel >> 8) & 0xff;
                sumB += pixel & 0xff;
            }
            for (int y = 0; y < height; y++) {
                dst[y * width + x] = 0xff000000 | ((sumR / span) << 16)
                        | ((sumG / span) << 8) | (sumB / span);
                int add = src[clamp(y + radius + 1, height) * width + x];
                int sub = src[clamp(y - radius, height) * width + x];
                sumA += ((add >>> 24) & 0xff) - ((sub >>> 24) & 0xff);
                sumR += ((add >> 16) & 0xff) - ((sub >> 16) & 0xff);
                sumG += ((add >> 8) & 0xff) - ((sub >> 8) & 0xff);
                sumB += (add & 0xff) - (sub & 0xff);
            }
        }
    }

    private static int clamp(int value, int limit) {
        if (value < 0) {
            return 0;
        }
        if (value >= limit) {
            return limit - 1;
        }
        return value;
    }
}
