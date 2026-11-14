package com.nas2screen.screensaver.source;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.nas2screen.screensaver.Diag;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Folder chosen through the system picker, read through {@link ContentResolver}. */
public final class SafPhotoSource implements PhotoSource {

    private static final String TAG = "SafSource";
    private static final int MAX_DEPTH = 16;
    private static final int MAX_FILES = 100_000;
    private static final int MAX_DIRS = 20_000;

    private static final String[] PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private final ContentResolver resolver;
    private final SourceSpec spec;
    private final Uri tree;

    public SafPhotoSource(Context context, SourceSpec spec) {
        this.resolver = context.getContentResolver();
        this.spec = spec;
        this.tree = Uri.parse(spec.localRef);
    }

    @Override
    public SourceSpec spec() {
        return spec;
    }

    @Override
    public List<PhotoRef> list() throws SourceException {
        final String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException e) {
            throw new SourceException(SourceException.Reason.NOT_CONFIGURED,
                    "bad tree uri " + tree, e);
        }

        List<PhotoRef> photos = new ArrayList<>();
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(rootId, "", 0));
        int dirs = 0;

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (++dirs > MAX_DIRS) {
                Diag.w(TAG, "folder limit reached, stopping scan");
                break;
            }
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, node.documentId);
            Cursor cursor = null;
            try {
                cursor = resolver.query(children, PROJECTION, null, null, null);
                if (cursor == null) {
                    continue;
                }
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    long size = cursor.isNull(3) ? 0L : cursor.getLong(3);
                    long modified = cursor.isNull(4) ? 0L : cursor.getLong(4);
                    if (name == null) {
                        continue;
                    }
                    boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                    if (isDirectory) {
                        if (node.depth < MAX_DEPTH && !PhotoFilter.isSkippedDir(name)) {
                            String childPath = node.path.isEmpty() ? name : node.path + "/" + name;
                            queue.add(new Node(documentId, childPath, node.depth + 1));
                        }
                    } else if (PhotoFilter.isImage(name) && !PhotoFilter.isSkippedFile(name)) {
                        Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
                        photos.add(new PhotoRef(documentUri.toString(), name, node.path, size, modified));
                        if (photos.size() >= MAX_FILES) {
                            Diag.w(TAG, "file limit reached, stopping scan");
                            queue.clear();
                            break;
                        }
                    }
                }
            } catch (SecurityException e) {
                throw new SourceException(SourceException.Reason.DENIED,
                        "no permission for " + node.path, e);
            } catch (RuntimeException e) {
                throw new SourceException(SourceException.Reason.IO,
                        "query failed in " + node.path + ": " + e, e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            Diag.i(TAG, "scanned " + node.path);
        }

        Collections.sort(photos, PhotoRef.BY_NAME);
        Diag.i(TAG, "found " + photos.size() + " photos");
        return photos;
    }

    @Override
    public InputStream open(PhotoRef ref) throws SourceException {
        try {
            InputStream in = resolver.openInputStream(Uri.parse(ref.id));
            if (in == null) {
                throw new SourceException(SourceException.Reason.UNREADABLE, ref.name);
            }
            return in;
        } catch (FileNotFoundException e) {
            throw new SourceException(SourceException.Reason.UNREADABLE, ref.name, e);
        } catch (SecurityException e) {
            throw new SourceException(SourceException.Reason.DENIED, ref.name, e);
        } catch (RuntimeException e) {
            throw new SourceException(SourceException.Reason.IO, ref.name + ": " + e, e);
        }
    }

    @Override
    public void close() {
        // nothing to release
    }

    private static final class Node {
        final String documentId;
        final String path;
        final int depth;

        Node(String documentId, String path, int depth) {
            this.documentId = documentId;
            this.path = path;
            this.depth = depth;
        }
    }
}
