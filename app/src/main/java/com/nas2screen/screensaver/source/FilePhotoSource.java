package com.nas2screen.screensaver.source;

import com.nas2screen.screensaver.Diag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Folder reached through a plain file path (internal storage or USB drive). */
public final class FilePhotoSource implements PhotoSource {

    private static final String TAG = "FileSource";
    private static final int MAX_DEPTH = 16;
    private static final int MAX_FILES = 100_000;
    private static final int MAX_DIRS = 20_000;

    private final SourceSpec spec;
    private final File root;

    public FilePhotoSource(SourceSpec spec) {
        this.spec = spec;
        this.root = new File(spec.localRef);
    }

    @Override
    public SourceSpec spec() {
        return spec;
    }

    @Override
    public List<PhotoRef> list() throws SourceException {
        if (!root.exists()) {
            throw new SourceException(SourceException.Reason.NOT_FOUND, root.getAbsolutePath());
        }
        if (!root.isDirectory()) {
            throw new SourceException(SourceException.Reason.NOT_FOUND,
                    root.getAbsolutePath() + " is not a folder");
        }
        if (!root.canRead()) {
            throw new SourceException(SourceException.Reason.DENIED, root.getAbsolutePath());
        }

        List<PhotoRef> photos = new ArrayList<>();
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(root, "", 0));
        int dirs = 0;

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (++dirs > MAX_DIRS) {
                Diag.w(TAG, "folder limit reached, stopping scan");
                break;
            }
            File[] children = node.dir.listFiles();
            if (children == null) {
                Diag.w(TAG, "cannot list " + node.dir.getAbsolutePath());
                continue;
            }
            for (File child : children) {
                String name = child.getName();
                if (child.isDirectory()) {
                    if (node.depth < MAX_DEPTH && !PhotoFilter.isSkippedDir(name)) {
                        String childPath = node.path.isEmpty() ? name : node.path + "/" + name;
                        queue.add(new Node(child, childPath, node.depth + 1));
                    }
                } else if (PhotoFilter.isImage(name) && !PhotoFilter.isSkippedFile(name)) {
                    photos.add(new PhotoRef(child.getAbsolutePath(), name, node.path,
                            child.length(), child.lastModified()));
                    if (photos.size() >= MAX_FILES) {
                        Diag.w(TAG, "file limit reached, stopping scan");
                        queue.clear();
                        break;
                    }
                }
            }
        }

        Collections.sort(photos, PhotoRef.BY_NAME);
        Diag.i(TAG, "found " + photos.size() + " photos in " + root.getAbsolutePath());
        return photos;
    }

    @Override
    public InputStream open(PhotoRef ref) throws SourceException {
        try {
            return new FileInputStream(new File(ref.id));
        } catch (FileNotFoundException e) {
            throw new SourceException(SourceException.Reason.UNREADABLE, ref.name, e);
        } catch (SecurityException e) {
            throw new SourceException(SourceException.Reason.DENIED, ref.name, e);
        }
    }

    @Override
    public void close() {
        // nothing to release
    }

    private static final class Node {
        final File dir;
        final String path;
        final int depth;

        Node(File dir, String path, int depth) {
            this.dir = dir;
            this.path = path;
            this.depth = depth;
        }
    }
}
