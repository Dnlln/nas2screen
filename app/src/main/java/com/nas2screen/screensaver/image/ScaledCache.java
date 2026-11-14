package com.nas2screen.screensaver.image;

import android.content.Context;
import android.graphics.Bitmap;

import com.nas2screen.screensaver.Diag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Disk cache of screen sized JPEG copies plus a scratch folder for downloading
 * originals. Nothing is kept in RAM here, so the cache cannot grow the memory
 * footprint of the slideshow.
 */
public final class ScaledCache {

    private static final String TAG = "ScaledCache";
    private static final long MAX_BYTES = 192L * 1024 * 1024;
    private static final long MIN_FREE_BYTES = 400L * 1024 * 1024;
    private static final int JPEG_QUALITY = 88;
    private static final long STALE_SPOOL_MS = 60L * 60 * 1000;

    private final File dir;
    private final File spoolDir;
    private long knownBytes = -1;

    public ScaledCache(Context context) {
        File cacheRoot = context.getCacheDir();
        dir = new File(cacheRoot, "scaled");
        spoolDir = new File(cacheRoot, "spool");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        spoolDir.mkdirs();
        cleanSpool();
    }

    /** Stable cache key; includes the screen size so the cache follows the TV. */
    public static String key(String kind, String id, long size, long modified, int width, int height) {
        String raw = kind + '|' + id + '|' + size + '|' + modified + '|' + width + 'x' + height;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    public File get(String key) {
        File file = new File(dir, key + ".jpg");
        if (!file.isFile() || file.length() == 0) {
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            return null;
        }
        // touch for LRU order
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(System.currentTimeMillis());
        return file;
    }

    public File newSpool() throws IOException {
        return File.createTempFile("photo", ".tmp", spoolDir);
    }

    /** Writes the (already scaled) bitmap into the cache; failures are ignored. */
    public void store(String key, Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) {
            return;
        }
        if (dir.getUsableSpace() < MIN_FREE_BYTES) {
            Diag.w(TAG, "not enough free space to cache, skipping");
            return;
        }
        File target = new File(dir, key + ".jpg");
        File part = new File(dir, key + ".part");
        // JPEG has no alpha channel; keep PNG/WebP photos with transparency intact
        Bitmap.CompressFormat format = bitmap.hasAlpha()
                ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
        try {
            try (OutputStream out = new FileOutputStream(part)) {
                if (!bitmap.compress(format, JPEG_QUALITY, out)) {
                    throw new IOException("compress returned false");
                }
            }
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            if (!part.renameTo(target)) {
                throw new IOException("cannot rename " + part);
            }
            knownBytes = -1;
            trim();
        } catch (Throwable t) {
            Diag.w(TAG, "cannot store cached image", t);
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        }
    }

    public long bytes() {
        if (knownBytes < 0) {
            knownBytes = computeBytes();
        }
        return knownBytes;
    }

    public void clear() {
        deleteChildren(dir);
        deleteChildren(spoolDir);
        knownBytes = 0;
        Diag.i(TAG, "cache cleared");
    }

    private long computeBytes() {
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                total += file.length();
            }
        }
        return total;
    }

    private void trim() {
        long total = computeBytes();
        if (total <= MAX_BYTES) {
            knownBytes = total;
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        List<File> ordered = new ArrayList<>();
        Collections.addAll(ordered, files);
        ordered.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long target = (long) (MAX_BYTES * 0.85);
        for (File file : ordered) {
            if (total <= target) {
                break;
            }
            long length = file.length();
            if (file.delete()) {
                total -= length;
            }
        }
        knownBytes = total;
        Diag.i(TAG, "cache trimmed to " + (total / (1024 * 1024)) + " MB");
    }

    private void cleanSpool() {
        File[] files = spoolDir.listFiles();
        if (files == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (File file : files) {
            if (now - file.lastModified() > STALE_SPOOL_MS) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static void deleteChildren(File parent) {
        File[] files = parent.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
