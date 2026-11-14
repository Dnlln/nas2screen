package com.nas2screen.screensaver.source;

import android.content.Context;

/** Builds the {@link PhotoSource} matching a {@link SourceSpec}. */
public final class SourceFactory {

    private SourceFactory() {
    }

    public static PhotoSource create(Context context, SourceSpec spec) throws SourceException {
        if (spec == null) {
            throw new SourceException(SourceException.Reason.NOT_CONFIGURED, "no spec");
        }
        if (!spec.isComplete()) {
            throw new SourceException(SourceException.Reason.NOT_CONFIGURED, "incomplete spec");
        }
        switch (spec.kind) {
            case LOCAL_URI:
                return new SafPhotoSource(context.getApplicationContext(), spec);
            case LOCAL_FILE:
                return new FilePhotoSource(spec);
            case SMB:
            default:
                return new SmbPhotoSource(spec);
        }
    }
}
