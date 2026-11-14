package com.nas2screen.screensaver.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;

import com.nas2screen.screensaver.Diag;
import com.nas2screen.screensaver.source.PhotoRef;
import com.nas2screen.screensaver.source.PhotoSource;
import com.nas2screen.screensaver.source.SourceException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Turns a photo of any size into two small bitmaps:
 * <ul>
 *   <li>{@code main} - fitted inside the screen, EXIF orientation applied;</li>
 *   <li>{@code blur} - at most 1/8 of the screen long side, for the letterbox background.</li>
 * </ul>
 * Originals are streamed to a scratch file first, so even a 100 MB photo never
 * lands in memory as a whole.
 */
public final class PhotoDecoder {

    private static final String TAG = "PhotoDecoder";
    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024;

    public static final class Frame {
        public final PhotoRef ref;
        public final Bitmap main;
        public final Bitmap blur;

        Frame(PhotoRef ref, Bitmap main, Bitmap blur) {
            this.ref = ref;
            this.main = main;
            this.blur = blur;
        }

        public long bytes() {
            long total = 0;
            if (main != null) {
                total += main.getByteCount();
            }
            if (blur != null) {
                total += blur.getByteCount();
            }
            return total;
        }

        public void recycle() {
            if (main != null && !main.isRecycled()) {
                main.recycle();
            }
            if (blur != null && !blur.isRecycled()) {
                blur.recycle();
            }
        }
    }

    private final ScaledCache cache;
    private final int boxWidth;
    private final int boxHeight;

    public PhotoDecoder(Context context, ScaledCache cache, int boxWidth, int boxHeight) {
        this.cache = cache;
        this.boxWidth = Math.max(64, boxWidth);
        this.boxHeight = Math.max(64, boxHeight);
    }

    public int boxWidth() {
        return boxWidth;
    }

    public int boxHeight() {
        return boxHeight;
    }

    public Frame decode(PhotoSource source, PhotoRef ref) throws SourceException {
        String key = ScaledCache.key(source.spec().kind.name(), ref.id, ref.size, ref.modified,
                boxWidth, boxHeight);

        Bitmap main = null;
        File cached = cache.get(key);
        if (cached != null) {
            main = decodeFile(cached);
            if (main == null) {
                Diag.w(TAG, "cached copy unreadable, dropping it: " + cached.getName());
                //noinspection ResultOfMethodCallIgnored
                cached.delete();
            }
        }

        if (main == null) {
            File spool = null;
            try {
                spool = cache.newSpool();
                long bytes = copyTo(source.open(ref), spool);
                Diag.i(TAG, "fetched " + ref.name + " (" + (bytes / 1024) + " KB)");
                main = decodeFile(spool);
                if (main != null) {
                    cache.store(key, main);
                }
            } catch (IOException e) {
                throw new SourceException(SourceException.Reason.IO,
                        ref.name + ": " + e.getMessage(), e);
            } catch (OutOfMemoryError e) {
                throw new SourceException(SourceException.Reason.UNREADABLE,
                        ref.name + ": out of memory", e);
            } finally {
                if (spool != null) {
                    //noinspection ResultOfMethodCallIgnored
                    spool.delete();
                }
            }
        }

        if (main == null) {
            throw new SourceException(SourceException.Reason.UNREADABLE, ref.name);
        }

        Bitmap blur;
        try {
            blur = Blur.background(main, Math.max(2, Math.max(boxWidth, boxHeight) / 10));
        } catch (OutOfMemoryError e) {
            blur = null;
            Diag.w(TAG, "no memory for blurred background", e);
        }
        return new Frame(ref, main, blur);
    }

    // ------------------------------------------------------------------ decode

    private Bitmap decodeFile(File file) {
        int orientation = readOrientation(file);
        boolean swap = orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_270;
        boolean needsTransform = orientation != ExifInterface.ORIENTATION_NORMAL
                && orientation != ExifInterface.ORIENTATION_UNDEFINED;

        // Remove the EXIF rotation first: then we know exactly what the platform
        // decoder returns and can rotate by ourselves, once, without guessing.
        boolean exifCleared = !needsTransform || clearOrientation(file);

        // Very large photos can still exceed the heap on a 2 GB TV, so retry with a
        // smaller target instead of dropping the photo.
        float[] factors = {1f, 0.6f, 0.35f};
        for (float factor : factors) {
            Bitmap bitmap = decodeOnce(file, orientation, swap, needsTransform, exifCleared, factor);
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    private Bitmap decodeOnce(File file, int orientation, boolean swap, boolean needsTransform,
                              boolean exifCleared, float qualityFactor) {
        final float factor = qualityFactor;
        boolean usedFallback = false;
        final int[] requested = new int[]{0, 0};
        Bitmap bitmap = null;
        try {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),
                    (decoder, info, src) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        int encodedWidth = info.getSize().getWidth();
                        int encodedHeight = info.getSize().getHeight();
                        if (encodedWidth <= 0 || encodedHeight <= 0) {
                            return;
                        }
                        int displayWidth = swap ? encodedHeight : encodedWidth;
                        int displayHeight = swap ? encodedWidth : encodedHeight;
                        float scale = Math.min(1f, Math.min(
                                (float) boxWidth / displayWidth,
                                (float) boxHeight / displayHeight)) * factor;
                        int targetWidth = Math.max(1, Math.round(displayWidth * scale));
                        int targetHeight = Math.max(1, Math.round(displayHeight * scale));
                        int preWidth = swap ? targetHeight : targetWidth;
                        int preHeight = swap ? targetWidth : targetHeight;
                        requested[0] = preWidth;
                        requested[1] = preHeight;
                        if (preWidth != encodedWidth || preHeight != encodedHeight) {
                            decoder.setTargetSize(preWidth, preHeight);
                        }
                    });
        } catch (OutOfMemoryError oom) {
            Diag.w(TAG, "out of memory decoding " + file.getName() + " at " + factor);
            return null;
        } catch (Throwable t) {
            Diag.w(TAG, "ImageDecoder failed for " + file.getName() + ": " + t);
            usedFallback = true;
            bitmap = decodeFallback(file, factor);
        }

        if (bitmap == null) {
            return null;
        }
        if (!needsTransform) {
            return bitmap;
        }

        boolean apply;
        if (usedFallback) {
            // BitmapFactory never applies EXIF, so the rotation is still ours.
            apply = true;
        } else if (exifCleared) {
            apply = true;
        } else {
            // EXIF could not be rewritten and the platform may have rotated already;
            // the requested size tells us what actually happened.
            apply = swap && requested[0] > 0
                    && bitmap.getWidth() == requested[0]
                    && bitmap.getHeight() == requested[1];
        }
        try {
            return apply ? applyTransform(bitmap, orientation) : bitmap;
        } catch (OutOfMemoryError oom) {
            Diag.w(TAG, "out of memory rotating " + file.getName());
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return null;
        }
    }

    private Bitmap decodeFallback(File file, float qualityFactor) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int width = bounds.outWidth;
            int height = bounds.outHeight;
            if (width <= 0 || height <= 0) {
                return null;
            }
            float scale = Math.min(1f, Math.min(
                    (float) boxWidth / width,
                    (float) boxHeight / height)) * qualityFactor;
            int targetWidth = Math.max(1, Math.round(width * scale));
            int targetHeight = Math.max(1, Math.round(height * scale));

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            int sample = 1;
            while (width / (sample * 2) > targetWidth * 2
                    && height / (sample * 2) > targetHeight * 2) {
                sample *= 2;
            }
            options.inSampleSize = sample;

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) {
                return null;
            }
            if (bitmap.getWidth() > targetWidth * 1.05f
                    || bitmap.getHeight() > targetHeight * 1.05f) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }
            return bitmap;
        } catch (OutOfMemoryError oom) {
            Diag.w(TAG, "out of memory decoding " + file.getName() + " (fallback)");
            return null;
        } catch (Throwable t) {
            Diag.w(TAG, "BitmapFactory failed for " + file.getName() + ": " + t);
            return null;
        }
    }

    private static Bitmap applyTransform(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
                return rotated;
            }
        } catch (Throwable t) {
            Diag.w(TAG, "cannot apply EXIF orientation " + orientation, t);
        }
        return bitmap;
    }

    private static int readOrientation(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Throwable t) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static boolean clearOrientation(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_NORMAL) {
                return true;
            }
            exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            exif.saveAttributes();
            return true;
        } catch (Throwable t) {
            Diag.w(TAG, "cannot rewrite EXIF orientation for " + file.getName(), t);
            return false;
        }
    }

    private static long copyTo(InputStream input, File target) throws IOException {
        long total = 0;
        try (InputStream in = input; OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                total += read;
                if (total > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("file larger than " + (MAX_DOWNLOAD_BYTES >> 20) + " MB");
                }
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }
        return total;
    }
}
