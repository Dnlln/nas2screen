package com.nas2screen.screensaver.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parsed SMB target: host + share + relative folder inside the share.
 *
 * <p>Accepts the formats a user may type:
 * <ul>
 *   <li>server {@code 192.168.88.23} + folder {@code Multimedia/Camera Uploads/2026/02}</li>
 *   <li>server {@code 192.168.88.23} + full UNC {@code \\192.168.88.23\Multimedia\Camera Uploads\2026\02}</li>
 *   <li>server empty + full UNC in the folder field</li>
 *   <li>{@code smb://192.168.88.23/Multimedia/...}</li>
 * </ul>
 */
public final class SmbPath {

    public final String host;
    public final String share;
    /** Folder inside the share, separated by backslashes, no leading/trailing separator. */
    public final String path;

    private SmbPath(String host, String share, String path) {
        this.host = host;
        this.share = share;
        this.path = path;
    }

    public static SmbPath parse(String serverField, String folderField) {
        String host = normalizeHost(serverField);
        String raw = folderField == null ? "" : folderField.trim();

        boolean unc = false;
        if (raw.startsWith("\\\\") || raw.startsWith("//")) {
            unc = true;
            raw = raw.substring(2);
        } else {
            int scheme = raw.indexOf("://");
            if (scheme > 0) {
                // smb://host/share/path - the first segment is always the host
                raw = raw.substring(scheme + 3);
                unc = true;
            }
            if (raw.startsWith("\\\\") || raw.startsWith("//")) {
                unc = true;
                raw = raw.substring(2);
            }
        }

        List<String> parts = split(raw);
        if (unc && !parts.isEmpty()) {
            String firstHost = stripTrailingDots(parts.remove(0));
            if (!firstHost.isEmpty() && (host.isEmpty() || host.equalsIgnoreCase(firstHost))) {
                host = firstHost;
            }
        }
        if (parts.isEmpty()) {
            return new SmbPath(host, "", "");
        }
        String share = parts.remove(0);
        return new SmbPath(host, share, join(parts));
    }

    public boolean isComplete() {
        return !host.isEmpty() && !share.isEmpty();
    }

    /** Folder relative to the share, as used for {@code DiskShare} operations. */
    public String sharePath() {
        return path;
    }

    /** {@code share\folder\sub} - the value shown in the folder field. */
    public String relativeFolder() {
        return path.isEmpty() ? share : share + "\\" + path;
    }

    /** {@code \\host\share\folder\sub}. */
    public String unc() {
        return "\\\\" + host + "\\" + relativeFolder();
    }

    @Override
    public String toString() {
        return unc();
    }

    // ---------------------------------------------------------------- helpers

    private static String normalizeHost(String serverField) {
        String h = serverField == null ? "" : serverField.trim();
        if (h.isEmpty()) {
            return "";
        }
        int scheme = h.indexOf("://");
        if (scheme > 0) {
            h = h.substring(scheme + 3);
        }
        while (h.startsWith("\\") || h.startsWith("/")) {
            h = h.substring(1);
        }
        while (h.endsWith("\\") || h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        h = stripTrailingDots(h);
        // a typed "192.168.88.23:445" still means the default SMB port
        int colon = h.lastIndexOf(':');
        if (colon > 0 && h.indexOf(']') < 0 && h.indexOf(':') == colon) {
            h = h.substring(0, colon);
        }
        return h;
    }

    private static String stripTrailingDots(String s) {
        String out = s.trim();
        while (out.endsWith(".")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static List<String> split(String raw) {
        String unified = raw.replace('/', '\\');
        List<String> parts = new ArrayList<>();
        for (String piece : unified.split("\\\\")) {
            String p = piece.trim();
            if (p.isEmpty() || ".".equals(p)) {
                continue;
            }
            if ("..".equals(p)) {
                continue;
            }
            parts.add(p);
        }
        return parts;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('\\');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /**
     * Normalizes the two settings fields so that the folder field always holds
     * {@code share\sub\folder}. Returns {@code {server, folder}}.
     */
    public static String[] normalizeFields(String serverField, String folderField) {
        SmbPath parsed = parse(serverField, folderField);
        String server = parsed.host.isEmpty() ? (serverField == null ? "" : serverField.trim()) : parsed.host;
        String folder = parsed.share.isEmpty() ? "" : parsed.relativeFolder();
        return new String[]{server, folder};
    }

    /** Lower-cased host without port, used for diagnostics. */
    public String hostKey() {
        return host.toLowerCase(Locale.ROOT);
    }
}
