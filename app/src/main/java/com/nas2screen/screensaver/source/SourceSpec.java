package com.nas2screen.screensaver.source;

import android.content.Context;

import com.nas2screen.screensaver.R;
import com.nas2screen.screensaver.net.SmbPath;

/** Immutable description of where photos come from. */
public final class SourceSpec {

    public enum Kind {
        /** Folder picked with the system picker (SAF tree). */
        LOCAL_URI,
        /** Folder chosen through the built-in browser (plain file path). */
        LOCAL_FILE,
        SMB
    }

    public final Kind kind;
    /** URI string for {@link Kind#LOCAL_URI}, absolute path for {@link Kind#LOCAL_FILE}. */
    public final String localRef;
    public final String localLabel;
    public final SmbPath smb;
    public final String user;
    public final String password;

    private SourceSpec(Kind kind, String localRef, String localLabel, SmbPath smb,
                       String user, String password) {
        this.kind = kind;
        this.localRef = localRef;
        this.localLabel = localLabel;
        this.smb = smb;
        this.user = user;
        this.password = password;
    }

    public static SourceSpec localUri(String uri, String label) {
        return new SourceSpec(Kind.LOCAL_URI, uri, label, null, "", "");
    }

    public static SourceSpec localFile(String path, String label) {
        return new SourceSpec(Kind.LOCAL_FILE, path, label, null, "", "");
    }

    public static SourceSpec smb(SmbPath path, String user, String password) {
        return new SourceSpec(Kind.SMB, null, null, path, user == null ? "" : user,
                password == null ? "" : password);
    }

    public boolean isSmb() {
        return kind == Kind.SMB;
    }

    public boolean isComplete() {
        if (kind == Kind.SMB) {
            return smb != null && smb.isComplete();
        }
        return localRef != null && !localRef.isEmpty();
    }

    /** Short human readable description used in the "current source" header. */
    public String describe(Context context) {
        if (kind == Kind.SMB) {
            String base = context.getString(R.string.current_source_smb);
            return smb == null ? base : base + " · " + smb.unc();
        }
        String label = localLabel;
        if (label == null || label.isEmpty()) {
            label = localRef == null ? "" : localRef;
        }
        return context.getString(R.string.current_source_local) + " · " + label;
    }
}
