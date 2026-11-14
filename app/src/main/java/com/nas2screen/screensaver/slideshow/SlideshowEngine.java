package com.nas2screen.screensaver.slideshow;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;

import com.nas2screen.screensaver.Diag;
import com.nas2screen.screensaver.Prefs;
import com.nas2screen.screensaver.R;
import com.nas2screen.screensaver.image.PhotoDecoder;
import com.nas2screen.screensaver.image.ScaledCache;
import com.nas2screen.screensaver.source.PhotoRef;
import com.nas2screen.screensaver.source.PhotoSource;
import com.nas2screen.screensaver.source.Playlist;
import com.nas2screen.screensaver.source.SourceException;
import com.nas2screen.screensaver.source.SourceFactory;
import com.nas2screen.screensaver.source.SourceSpec;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the infinite slideshow: keeps at most two decoded frames in memory, one
 * photo preloaded ahead, retries the source every 30 seconds when it fails.
 */
public final class SlideshowEngine {

    private static final String TAG = "Slideshow";
    private static final long RETRY_DELAY_MS = 30_000L;
    private static final int MAX_CONSECUTIVE_FAILURES = 8;
    private static final int TRANSITION_MS = 500;

    private final Context context;
    private final ScreenView view;
    private final Handler main = new Handler(Looper.getMainLooper());

    private ScaledCache cache;
    private ExecutorService io;
    private PhotoSource source;
    private PhotoDecoder decoder;
    private Playlist<PhotoRef> playlist;

    private PhotoDecoder.Frame pending;
    private boolean decoding;
    private boolean waitingForAdvance;
    private boolean shownAnything;
    private boolean running;
    private int failures;
    private int intervalMs;

    private final Runnable advanceRunnable = this::advance;
    private final Runnable retryRunnable = this::retry;

    public SlideshowEngine(Context context, ScreenView view) {
        this.context = context.getApplicationContext();
        this.view = view;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        shownAnything = false;
        failures = 0;
        waitingForAdvance = false;
        decoding = false;

        Prefs prefs = Prefs.get(context);
        intervalMs = prefs.intervalSeconds() * 1000;
        view.setTransitionMs(TRANSITION_MS);
        view.setClockVisible(prefs.clock());
        cache = new ScaledCache(context);
        io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "slideshow-io");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });

        if (!prefs.isConfigured()) {
            view.showMessage(context.getString(R.string.msg_choose_source),
                    context.getString(R.string.msg_choose_source_hint));
            return;
        }
        connect();
    }

    public void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
        if (pending != null) {
            pending.recycle();
            pending = null;
        }
        releaseSource();
        if (io != null) {
            io.shutdownNow();
            io = null;
        }
        if (view != null) {
            view.clear();
        }
        Diag.i(TAG, "stopped");
    }

    // ------------------------------------------------------------------ source

    private void connect() {
        Prefs prefs = Prefs.get(context);
        final SourceSpec spec = prefs.toSpec();
        if (spec == null) {
            view.showMessage(context.getString(R.string.msg_choose_source),
                    context.getString(R.string.msg_choose_source_hint));
            return;
        }
        view.showMessage(context.getString(R.string.msg_loading), null);
        final ExecutorService executor = io;
        if (executor == null) {
            return;
        }
        executor.execute(() -> {
            PhotoSource created = null;
            try {
                created = SourceFactory.create(context, spec);
                List<PhotoRef> photos = created.list();
                final PhotoSource ready = created;
                created = null;
                main.post(() -> onListed(ready, photos));
            } catch (SourceException e) {
                Diag.w(TAG, "source failed: " + e.getMessage());
                main.post(() -> onSourceError(e.localized(context), e.getMessage()));
            } catch (Throwable t) {
                Diag.e(TAG, "source crashed", t);
                main.post(() -> onSourceError(
                        context.getString(R.string.err_source_unavailable),
                        t.getClass().getSimpleName() + ": " + t.getMessage()));
            } finally {
                if (created != null) {
                    created.close();
                }
            }
        });
    }

    private void onListed(PhotoSource listed, List<PhotoRef> photos) {
        if (!running) {
            listed.close();
            return;
        }
        releaseSource();
        source = listed;
        Prefs prefs = Prefs.get(context);
        playlist = new Playlist<>(photos, prefs.shuffle(), new Random());
        if (photos.isEmpty()) {
            onSourceError(context.getString(R.string.err_no_files), null);
            return;
        }
        failures = 0;
        Diag.i(TAG, "ready, " + photos.size() + " photos, interval " + (intervalMs / 1000) + "s, shuffle="
                + prefs.shuffle());
        ensureDecoder();
        view.hideMessage();
        preloadNext();
    }

    private void releaseSource() {
        if (source != null) {
            source.close();
            source = null;
        }
    }

    // ------------------------------------------------------------------ frames

    private void ensureDecoder() {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
        if (decoder == null || decoder.boxWidth() != width || decoder.boxHeight() != height) {
            decoder = new PhotoDecoder(context, cache, width, height);
            Diag.i(TAG, "decoder for " + width + "x" + height);
        }
    }

    private void preloadNext() {
        if (!running || decoding || waitingForAdvance || pending != null) {
            return;
        }
        if (source == null || playlist == null || decoder == null || io == null) {
            return;
        }
        final PhotoRef ref = playlist.next();
        if (ref == null) {
            return;
        }
        final PhotoSource currentSource = source;
        final PhotoDecoder currentDecoder = decoder;
        decoding = true;
        io.execute(() -> {
            PhotoDecoder.Frame frame = null;
            try {
                frame = currentDecoder.decode(currentSource, ref);
            } catch (SourceException e) {
                Diag.w(TAG, "skip " + ref.name + ": " + e.getMessage());
            } catch (OutOfMemoryError e) {
                Diag.e(TAG, "out of memory while decoding " + ref.name, e);
            } catch (Throwable t) {
                Diag.e(TAG, "decode crashed for " + ref.name, t);
            }
            final PhotoDecoder.Frame result = frame;
            main.post(() -> onFrame(ref, result));
        });
    }

    private void onFrame(PhotoRef ref, PhotoDecoder.Frame frame) {
        decoding = false;
        if (!running) {
            if (frame != null) {
                frame.recycle();
            }
            return;
        }
        if (frame == null) {
            failures++;
            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                onSourceError(context.getString(R.string.err_source_unavailable), ref.name);
                return;
            }
            preloadNext();
            return;
        }
        failures = 0;
        if (waitingForAdvance || !shownAnything) {
            waitingForAdvance = false;
            presentAndContinue(frame);
        } else {
            if (pending != null) {
                pending.recycle();
            }
            pending = frame;
        }
    }

    private void presentAndContinue(PhotoDecoder.Frame frame) {
        view.hideMessage();
        view.showFrame(frame.main, frame.blur, shownAnything);
        shownAnything = true;
        scheduleAdvance();
        preloadNext();
    }

    private void scheduleAdvance() {
        main.removeCallbacks(advanceRunnable);
        main.postDelayed(advanceRunnable, Math.max(1000, intervalMs));
    }

    private void advance() {
        if (!running) {
            return;
        }
        if (pending != null) {
            PhotoDecoder.Frame frame = pending;
            pending = null;
            presentAndContinue(frame);
        } else {
            // still downloading the next photo: show it as soon as it is ready
            waitingForAdvance = true;
        }
    }

    // ------------------------------------------------------------------- error

    private void onSourceError(String message, String detail) {
        if (!running) {
            return;
        }
        waitingForAdvance = false;
        if (pending != null) {
            pending.recycle();
            pending = null;
        }
        String reason = message;
        if (detail != null && !detail.isEmpty() && !detail.equals(message)) {
            reason = message + "\n" + detail;
        }
        view.showMessage(context.getString(R.string.err_source_unavailable),
                reason + "\n" + context.getString(R.string.retry_in, (int) (RETRY_DELAY_MS / 1000)));
        Prefs.get(context).setLastError(message + (detail == null || detail.isEmpty() ? "" : " / " + detail));
        Diag.w(TAG, "source error: " + message + " / " + detail);
        releaseSource();
        main.removeCallbacks(retryRunnable);
        main.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void retry() {
        if (!running) {
            return;
        }
        Diag.i(TAG, "retrying source");
        connect();
    }
}
