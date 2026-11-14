package com.nas2screen.screensaver;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Logging that also keeps a small rotating log file on the device, so problems on
 * a TV (where logcat is not reachable) can be diagnosed from the settings screen.
 */
public final class Diag {

    private static final String TAG = "Nas2Screen";
    private static final long MAX_LOG_BYTES = 96 * 1024L;

    private static File logFile;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT);

    private Diag() {
    }

    public static void init(Context context) {
        try {
            logFile = new File(context.getApplicationContext().getFilesDir(), "app.log");
        } catch (Throwable t) {
            logFile = null;
        }
    }

    public static void i(String tag, String message) {
        Log.i(TAG, tag + ": " + message);
        append("I", tag, message, null);
    }

    public static void w(String tag, String message) {
        Log.w(TAG, tag + ": " + message);
        append("W", tag, message, null);
    }

    public static void w(String tag, String message, Throwable t) {
        Log.w(TAG, tag + ": " + message, t);
        append("W", tag, message, t);
    }

    public static void e(String tag, String message, Throwable t) {
        Log.e(TAG, tag + ": " + message, t);
        append("E", tag, message, t);
    }

    public static void e(String tag, String message) {
        e(tag, message, null);
    }

    public static File logFile() {
        return logFile;
    }

    public static String readLog() {
        if (logFile == null || !logFile.exists()) {
            return "";
        }
        try {
            byte[] data = new byte[(int) Math.min(Integer.MAX_VALUE - 8, logFile.length())];
            try (java.io.FileInputStream in = new java.io.FileInputStream(logFile)) {
                int offset = 0;
                while (offset < data.length) {
                    int read = in.read(data, offset, data.length - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static void clearLog() {
        if (logFile != null && logFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logFile.delete();
        }
    }

    private static synchronized void append(String level, String tag, String message, Throwable t) {
        if (logFile == null) {
            return;
        }
        try {
            if (logFile.exists() && logFile.length() > MAX_LOG_BYTES) {
                File old = new File(logFile.getParentFile(), "app.old.log");
                //noinspection ResultOfMethodCallIgnored
                old.delete();
                //noinspection ResultOfMethodCallIgnored
                logFile.renameTo(old);
            }
            try (Writer w = new OutputStreamWriter(
                    new FileOutputStream(logFile, true), StandardCharsets.UTF_8)) {
                w.write(TIME.format(new Date()));
                w.write(' ');
                w.write(level);
                w.write(' ');
                w.write(tag);
                w.write(": ");
                w.write(message == null ? "" : message);
                if (t != null) {
                    w.write(" | ");
                    w.write(t.getClass().getSimpleName());
                    w.write(": ");
                    w.write(String.valueOf(t.getMessage()));
                }
                w.write('\n');
            }
        } catch (Throwable ignored) {
            // logging must never break the app
        }
    }
}
