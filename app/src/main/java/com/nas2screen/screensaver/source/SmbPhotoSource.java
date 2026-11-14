package com.nas2screen.screensaver.source;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.Share;
import com.nas2screen.screensaver.Diag;
import com.nas2screen.screensaver.net.SmbPath;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Photos from an SMB 2/3 share (the target NAS speaks SMB 2.1; SMB 1 is never
 * negotiated). One connection is kept alive for the whole slideshow session and
 * is dropped on any transport error, then rebuilt on the next call.
 */
public final class SmbPhotoSource implements PhotoSource {

    private static final String TAG = "SmbSource";
    private static final int MAX_DEPTH = 16;
    private static final int MAX_FILES = 100_000;
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 90_000;

    private final SourceSpec spec;
    private final SmbPath target;

    private SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare share;

    public SmbPhotoSource(SourceSpec spec) {
        this.spec = spec;
        this.target = spec.smb;
    }

    @Override
    public SourceSpec spec() {
        return spec;
    }

    // ------------------------------------------------------------------ listing

    @Override
    public List<PhotoRef> list() throws SourceException {
        if (target == null) {
            throw new SourceException(SourceException.Reason.NOT_FOUND, "no SMB target");
        }
        DiskShare diskShare = share();
        String base = target.sharePath();
        if (!base.isEmpty()) {
            try {
                if (!diskShare.folderExists(base)) {
                    throw new SourceException(SourceException.Reason.NOT_FOUND, target.unc());
                }
            } catch (SMBApiException e) {
                invalidate();
                throw mapSmb(e);
            } catch (RuntimeException e) {
                invalidate();
                throw mapIo(e);
            }
        }
        List<PhotoRef> photos = new ArrayList<>();
        collect(diskShare, base, 0, photos);
        Collections.sort(photos, PhotoRef.BY_NAME);
        Diag.i(TAG, "listed " + photos.size() + " photos in " + target.unc());
        return photos;
    }

    private void collect(DiskShare diskShare, String relative, int depth, List<PhotoRef> out)
            throws SourceException {
        List<FileIdBothDirectoryInformation> entries;
        try {
            entries = diskShare.list(relative);
        } catch (SMBApiException e) {
            if (depth == 0) {
                invalidate();
                throw mapSmb(e);
            }
            Diag.w(TAG, "cannot list " + relative + ": " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            if (depth == 0) {
                invalidate();
                throw mapIo(e);
            }
            Diag.w(TAG, "cannot list " + relative + ": " + e);
            return;
        }
        for (FileIdBothDirectoryInformation entry : entries) {
            String name = entry.getFileName();
            if (name == null || ".".equals(name) || "..".equals(name)) {
                continue;
            }
            boolean directory = EnumWithValue.EnumUtils.isSet(
                    entry.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
            String child = relative.isEmpty() ? name : relative + "\\" + name;
            if (directory) {
                if (depth < MAX_DEPTH && !PhotoFilter.isSkippedDir(name)) {
                    collect(diskShare, child, depth + 1, out);
                    if (out.size() >= MAX_FILES) {
                        Diag.w(TAG, "file limit reached, stopping scan");
                        return;
                    }
                }
            } else if (PhotoFilter.isImage(name) && !PhotoFilter.isSkippedFile(name)) {
                long modified = 0L;
                try {
                    modified = entry.getLastWriteTime() == null ? 0L : entry.getLastWriteTime().toEpochMillis();
                } catch (RuntimeException ignored) {
                    // keep 0
                }
                out.add(new PhotoRef(child, name, relative, entry.getEndOfFile(), modified));
                if (out.size() >= MAX_FILES) {
                    Diag.w(TAG, "file limit reached, stopping scan");
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------------- open

    @Override
    public InputStream open(PhotoRef ref) throws SourceException {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            DiskShare diskShare = share();
            com.hierynomus.smbj.share.File file = null;
            try {
                file = diskShare.openFile(ref.id,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        EnumSet.allOf(SMB2ShareAccess.class),
                        SMB2CreateDisposition.FILE_OPEN,
                        null);
                return new SmbStream(file.getInputStream(), file);
            } catch (SMBApiException e) {
                closeQuietly(file);
                invalidate();
                throw mapSmb(e);
            } catch (RuntimeException e) {
                closeQuietly(file);
                invalidate();
                last = e;
                Diag.w(TAG, "read attempt " + attempt + " failed for " + ref.id + ": " + e);
            }
        }
        throw mapIo(last == null ? new IOException("open failed") : last);
    }

    // -------------------------------------------------------------- connection

    private synchronized DiskShare share() throws SourceException {
        if (share != null && share.isConnected()) {
            return share;
        }
        invalidate();
        try {
            client = new SMBClient(SmbConfig.builder()
                    .withTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .withSoTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build());
            Diag.i(TAG, "connecting to " + target.host + ", share '" + target.share + "'");
            connection = client.connect(target.host);
            session = connection.authenticate(authentication());
            Share connected = session.connectShare(target.share);
            if (!(connected instanceof DiskShare)) {
                throw new SourceException(SourceException.Reason.IO,
                        "share is not a disk share: " + target.share);
            }
            share = (DiskShare) connected;
            Diag.i(TAG, "connected to " + target.unc());
            return share;
        } catch (SMBApiException e) {
            invalidate();
            throw mapSmb(e);
        } catch (SourceException e) {
            invalidate();
            throw e;
        } catch (IOException | RuntimeException e) {
            invalidate();
            throw mapIo(e);
        }
    }

    private AuthenticationContext authentication() {
        String user = spec.user == null ? "" : spec.user.trim();
        if (user.isEmpty()) {
            return AuthenticationContext.anonymous();
        }
        String domain = "";
        String name = user;
        int backslash = user.indexOf('\\');
        if (backslash > 0) {
            domain = user.substring(0, backslash);
            name = user.substring(backslash + 1);
        } else {
            int at = user.indexOf('@');
            if (at > 0) {
                domain = user.substring(at + 1);
                name = user.substring(0, at);
            }
        }
        char[] password = spec.password == null ? new char[0] : spec.password.toCharArray();
        return new AuthenticationContext(name, password, domain);
    }

    private synchronized void invalidate() {
        closeQuietly(share);
        share = null;
        try {
            if (session != null) {
                session.close();
            }
        } catch (Throwable t) {
            Diag.w(TAG, "session close failed", t);
        }
        session = null;
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Throwable t) {
            Diag.w(TAG, "connection close failed", t);
        }
        connection = null;
        try {
            if (client != null) {
                client.close();
            }
        } catch (Throwable t) {
            Diag.w(TAG, "client close failed", t);
        }
        client = null;
    }

    @Override
    public void close() {
        invalidate();
    }

    // ----------------------------------------------------------- error mapping

    private SourceException mapSmb(SMBApiException e) {
        String code = e.getStatus() == null ? "" : e.getStatus().name();
        String detail = code + " · " + target.unc();
        if (contains(code, "LOGON_FAILURE", "ACCOUNT_DISABLED", "ACCOUNT_RESTRICTION",
                "PASSWORD_EXPIRED", "PASSWORD_MUST_CHANGE", "INVALID_LOGON_HOURS",
                "ACCESS_DENIED", "INVALID_PARAMETER")) {
            return new SourceException(SourceException.Reason.DENIED, detail, e);
        }
        if (contains(code, "BAD_NETWORK_NAME", "OBJECT_NAME_NOT_FOUND", "OBJECT_PATH_NOT_FOUND",
                "NOT_A_DIRECTORY", "DELETED", "FILE_IS_A_DIRECTORY")) {
            return new SourceException(SourceException.Reason.NOT_FOUND, detail, e);
        }
        if (contains(code, "NOT_SUPPORTED", "NOT_IMPLEMENTED", "INVALID_DEVICE_REQUEST")) {
            return new SourceException(SourceException.Reason.PROTOCOL, detail, e);
        }
        if (contains(code, "TIMEOUT")) {
            return new SourceException(SourceException.Reason.TIMEOUT, detail, e);
        }
        if (contains(code, "NETWORK_UNREACHABLE", "HOST_UNREACHABLE", "CONNECTION_DISCONNECTED",
                "CONNECTION_RESET", "NETWORK_NAME_DELETED", "USER_SESSION_DELETED",
                "NETWORK_SESSION_EXPIRED", "CONNECTION_REFUSED", "IO_DEVICE_ERROR")) {
            return new SourceException(SourceException.Reason.UNREACHABLE, detail, e);
        }
        return new SourceException(SourceException.Reason.IO, detail, e);
    }

    private SourceException mapIo(Throwable e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        Throwable cause = e.getCause();
        String causeName = cause == null ? "" : cause.getClass().getSimpleName();
        String causeLower = cause == null || cause.getMessage() == null
                ? "" : cause.getMessage().toLowerCase(Locale.ROOT);

        if (lower.contains("dialect") || lower.contains("no common")
                || causeLower.contains("dialect")) {
            return new SourceException(SourceException.Reason.PROTOCOL, message, e);
        }
        if (e instanceof SocketTimeoutException || cause instanceof SocketTimeoutException
                || lower.contains("timed out") || causeName.contains("Timeout")) {
            return new SourceException(SourceException.Reason.TIMEOUT,
                    message.isEmpty() ? "timeout" : message, e);
        }
        if (e instanceof UnknownHostException || cause instanceof UnknownHostException
                || lower.contains("unknown host")) {
            return new SourceException(SourceException.Reason.UNREACHABLE,
                    target.host + " — unknown host", e);
        }
        if (e instanceof ConnectException || cause instanceof ConnectException
                || lower.contains("refused") || lower.contains("unreachable")
                || lower.contains("no route")) {
            return new SourceException(SourceException.Reason.UNREACHABLE,
                    target.host + (message.isEmpty() ? "" : " — " + message), e);
        }
        if (lower.contains("logon") || lower.contains("authentication")) {
            return new SourceException(SourceException.Reason.DENIED, message, e);
        }
        return new SourceException(SourceException.Reason.IO,
                message.isEmpty() ? e.getClass().getSimpleName() : message, e);
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void closeQuietly(DiskShare value) {
        if (value == null) {
            return;
        }
        try {
            value.close();
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static void closeQuietly(com.hierynomus.smbj.share.File value) {
        if (value == null) {
            return;
        }
        try {
            value.close();
        } catch (Throwable ignored) {
            // ignore
        }
    }

    /** Closes the SMB read handle together with the stream. */
    private static final class SmbStream extends InputStream {

        private final InputStream delegate;
        private final com.hierynomus.smbj.share.File file;

        SmbStream(InputStream delegate, com.hierynomus.smbj.share.File file) {
            this.delegate = delegate;
            this.file = file;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public long skip(long count) throws IOException {
            return delegate.skip(count);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                closeQuietly(file);
            }
        }
    }
}
