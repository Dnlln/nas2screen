package com.nas2screen.screensaver.source;

import java.io.Closeable;
import java.io.InputStream;
import java.util.List;

/** A photo source that can be listed and read. Implementations are used from one background thread. */
public interface PhotoSource extends Closeable {

    SourceSpec spec();

    /** All photos, sorted, recursive into sub folders. */
    List<PhotoRef> list() throws SourceException;

    /** Opens the content of a photo. Caller closes the stream. */
    InputStream open(PhotoRef ref) throws SourceException;

    @Override
    void close();
}
