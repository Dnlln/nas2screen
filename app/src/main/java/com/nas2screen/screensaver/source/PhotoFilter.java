package com.nas2screen.screensaver.source;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which file names are photos, and which folders are service folders that must
 * not be scanned (NAS recycle bins, thumbnail caches and so on).
 */
public final class PhotoFilter {

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "webp", "heic", "heif"
    ));

    private static final Set<String> SKIPPED_DIRECTORIES = new HashSet<>(Arrays.asList(
            "@eadir", "@recycle", "@recently-snapshot", "@recently_snapshot",
            "#recycle", "#snapshot", "$recycle.bin", "system volume information",
            "lost+found", "recycler", "recycled", "found.000", ".trash", ".trashes",
            ".thumbnails", "thumbnails", "lost.dir", "android", "losts"
    ));

    private static final Set<String> SKIPPED_FILES = new HashSet<>(Arrays.asList(
            "thumbs.db", "desktop.ini", ".ds_store", "picasa.ini", ".picasa.ini"
    ));

    private PhotoFilter() {
    }

    public static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isImage(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(extension(name));
    }

    public static boolean isSkippedDir(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        if (name.startsWith(".") || name.startsWith("$") || name.startsWith("~")) {
            return true;
        }
        return SKIPPED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isSkippedFile(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        return SKIPPED_FILES.contains(name.toLowerCase(Locale.ROOT));
    }
}
