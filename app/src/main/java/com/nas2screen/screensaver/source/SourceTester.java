package com.nas2screen.screensaver.source;

import android.content.Context;

import com.nas2screen.screensaver.Diag;
import com.nas2screen.screensaver.R;

import java.util.List;

/** Blocking "test connection" check used by the settings screen. */
public final class SourceTester {

    private static final String TAG = "SourceTester";

    public static final class Result {
        public final boolean ok;
        public final int count;
        public final String message;

        Result(boolean ok, int count, String message) {
            this.ok = ok;
            this.count = count;
            this.message = message;
        }
    }

    private SourceTester() {
    }

    public static Result test(Context context, SourceSpec spec) {
        if (spec == null || !spec.isComplete()) {
            return new Result(false, 0, context.getString(R.string.test_need_values));
        }
        PhotoSource source = null;
        try {
            source = SourceFactory.create(context, spec);
            List<PhotoRef> photos = source.list();
            int count = photos.size();
            Diag.i(TAG, "test ok, " + count + " photos, " + spec.describe(context));
            if (count == 0) {
                return new Result(true, 0, context.getString(R.string.test_ok_empty));
            }
            return new Result(true, count, context.getString(R.string.test_ok, count));
        } catch (SourceException e) {
            Diag.w(TAG, "test failed: " + e.getMessage() + " / " + spec.describe(context));
            return new Result(false, 0, e.localized(context));
        } catch (OutOfMemoryError e) {
            Diag.e(TAG, "test out of memory", e);
            return new Result(false, 0, context.getString(R.string.err_source_unavailable));
        } catch (Throwable t) {
            Diag.e(TAG, "test crashed", t);
            return new Result(false, 0, context.getString(R.string.err_io, String.valueOf(t.getMessage())));
        } finally {
            if (source != null) {
                source.close();
            }
        }
    }
}
