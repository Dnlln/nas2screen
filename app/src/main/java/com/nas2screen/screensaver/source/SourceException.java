package com.nas2screen.screensaver.source;

import android.content.Context;

import com.nas2screen.screensaver.R;

/** Source failure with a reason that can be shown to the user. */
public class SourceException extends Exception {

    public enum Reason {
        NOT_CONFIGURED,
        NO_FILES,
        NOT_FOUND,
        DENIED,
        UNREACHABLE,
        TIMEOUT,
        PROTOCOL,
        UNREADABLE,
        IO
    }

    public final Reason reason;
    public final String detail;

    public SourceException(Reason reason, String detail) {
        this(reason, detail, null);
    }

    public SourceException(Reason reason, String detail, Throwable cause) {
        super(reason + (detail == null || detail.isEmpty() ? "" : ": " + detail), cause);
        this.reason = reason;
        this.detail = detail == null ? "" : detail;
    }

    /** Localized, user friendly text. */
    public String localized(Context context) {
        switch (reason) {
            case NOT_CONFIGURED:
                return context.getString(R.string.err_not_configured);
            case NO_FILES:
                return context.getString(R.string.err_no_files);
            case NOT_FOUND:
                return context.getString(R.string.err_not_found, firstLine());
            case DENIED:
                return context.getString(R.string.err_denied);
            case UNREACHABLE:
                return context.getString(R.string.err_unreachable, firstLine());
            case TIMEOUT:
                return context.getString(R.string.err_timeout);
            case PROTOCOL:
                return context.getString(R.string.err_protocol);
            case UNREADABLE:
                return context.getString(R.string.err_unreadable, firstLine());
            case IO:
            default:
                return context.getString(R.string.err_io, firstLine());
        }
    }

    private String firstLine() {
        if (detail == null || detail.isEmpty()) {
            return "?";
        }
        int nl = detail.indexOf('\n');
        String text = nl > 0 ? detail.substring(0, nl) : detail;
        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }
}
