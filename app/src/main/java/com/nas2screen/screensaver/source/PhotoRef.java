package com.nas2screen.screensaver.source;

import java.util.Comparator;

/** One photo in a source: its stable id plus a little metadata. */
public final class PhotoRef {

    /** Stable identifier: document URI for SAF, absolute path for files, share-relative path for SMB. */
    public final String id;
    public final String name;
    /** Human readable folder (for diagnostics only). */
    public final String folder;
    public final long size;
    public final long modified;

    public PhotoRef(String id, String name, String folder, long size, long modified) {
        this.id = id;
        this.name = name;
        this.folder = folder == null ? "" : folder;
        this.size = size;
        this.modified = modified;
    }

    public static final Comparator<PhotoRef> BY_NAME = (a, b) -> {
        int byName = String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
        return byName != 0 ? byName : a.id.compareTo(b.id);
    };

    @Override
    public String toString() {
        return id;
    }
}
