package com.nas2screen.screensaver;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.nas2screen.screensaver.net.SmbPath;
import com.nas2screen.screensaver.source.SourceSpec;

/** All user settings. Credentials live in encrypted preferences. */
public final class Prefs {

    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_SMB = "smb";

    public static final int MIN_INTERVAL = 5;
    public static final int MAX_INTERVAL = 3600;
    public static final int DEFAULT_INTERVAL = 10;

    private static final String FILE = "settings";
    private static final String SECURE_FILE = "settings_secure";

    private static volatile Prefs instance;

    private final SharedPreferences prefs;
    private final SharedPreferences secure;

    public static Prefs get(Context context) {
        Prefs local = instance;
        if (local == null) {
            synchronized (Prefs.class) {
                local = instance;
                if (local == null) {
                    local = new Prefs(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    private Prefs(Context app) {
        prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        secure = openSecure(app);
    }

    private static SharedPreferences openSecure(Context app) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    SECURE_FILE,
                    masterKeyAlias,
                    app,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Throwable t) {
            // Some TV firmwares have a broken keystore; still keep the app usable.
            Diag.e("Prefs", "Encrypted preferences unavailable, using plain file", t);
            return app.getSharedPreferences(SECURE_FILE + "_plain", Context.MODE_PRIVATE);
        }
    }

    // ------------------------------------------------------------- source type

    public String sourceType() {
        return prefs.getString("source_type", SOURCE_LOCAL);
    }

    public void setSourceType(String type) {
        prefs.edit().putString("source_type", type).apply();
    }

    public boolean isSmb() {
        return SOURCE_SMB.equals(sourceType());
    }

    // ------------------------------------------------------------------ local

    public String localUri() {
        return prefs.getString("local_uri", "");
    }

    public String localPath() {
        return prefs.getString("local_path", "");
    }

    public String localLabel() {
        String label = prefs.getString("local_label", "");
        if (label != null && !label.isEmpty()) {
            return label;
        }
        if (!localUri().isEmpty()) {
            return localUri();
        }
        return localPath();
    }

    public void setLocalUri(String uri, String label) {
        prefs.edit()
                .putString("local_uri", uri == null ? "" : uri)
                .putString("local_path", "")
                .putString("local_label", label == null ? "" : label)
                .apply();
    }

    public void setLocalPath(String path, String label) {
        prefs.edit()
                .putString("local_path", path == null ? "" : path)
                .putString("local_uri", "")
                .putString("local_label", label == null ? "" : label)
                .apply();
    }

    // -------------------------------------------------------------------- smb

    public String smbServer() {
        return prefs.getString("smb_server", "");
    }

    public String smbFolder() {
        return prefs.getString("smb_folder", "");
    }

    public String smbUser() {
        return secure.getString("smb_user", "");
    }

    public String smbPassword() {
        return secure.getString("smb_password", "");
    }

    public void setSmb(String server, String folder, String user, String password) {
        prefs.edit()
                .putString("smb_server", server == null ? "" : server)
                .putString("smb_folder", folder == null ? "" : folder)
                .apply();
        //noinspection ApplySharedPref
        secure.edit()
                .putString("smb_user", user == null ? "" : user)
                .putString("smb_password", password == null ? "" : password)
                .commit();
    }

    // -------------------------------------------------------------- slideshow

    public int intervalSeconds() {
        int value = prefs.getInt("interval", DEFAULT_INTERVAL);
        return Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, value));
    }

    public void setIntervalSeconds(int seconds) {
        prefs.edit().putInt("interval", Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, seconds))).apply();
    }

    public boolean shuffle() {
        return prefs.getBoolean("shuffle", true);
    }

    public void setShuffle(boolean value) {
        prefs.edit().putBoolean("shuffle", value).apply();
    }

    public boolean clock() {
        return prefs.getBoolean("clock", true);
    }

    public void setClock(boolean value) {
        prefs.edit().putBoolean("clock", value).apply();
    }

    // ------------------------------------------------------------ diagnostics

    public String lastError() {
        return prefs.getString("last_error", "");
    }

    public void setLastError(String text) {
        prefs.edit().putString("last_error", text == null ? "" : text).apply();
    }

    // --------------------------------------------------------------- snapshot

    /** Builds the source description from the saved settings, or {@code null}. */
    public SourceSpec toSpec() {
        if (isSmb()) {
            SmbPath path = SmbPath.parse(smbServer(), smbFolder());
            if (!path.isComplete()) {
                return null;
            }
            return SourceSpec.smb(path, smbUser(), smbPassword());
        }
        if (!localUri().isEmpty()) {
            return SourceSpec.localUri(localUri(), localLabel());
        }
        if (!localPath().isEmpty()) {
            return SourceSpec.localFile(localPath(), localLabel());
        }
        return null;
    }

    public boolean isConfigured() {
        return toSpec() != null;
    }
}
