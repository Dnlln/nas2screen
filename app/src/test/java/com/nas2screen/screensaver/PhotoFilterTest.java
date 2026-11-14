package com.nas2screen.screensaver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nas2screen.screensaver.source.PhotoFilter;

import org.junit.Test;

public class PhotoFilterTest {

    @Test
    public void acceptsSupportedImageTypes() {
        assertTrue(PhotoFilter.isImage("IMG_0001.JPG"));
        assertTrue(PhotoFilter.isImage("photo.jpeg"));
        assertTrue(PhotoFilter.isImage("scan.PNG"));
        assertTrue(PhotoFilter.isImage("web.webp"));
        assertTrue(PhotoFilter.isImage("pixel.heic"));
        assertTrue(PhotoFilter.isImage("pixel.HEIF"));
    }

    @Test
    public void rejectsUnsupportedOrBrokenNames() {
        assertFalse(PhotoFilter.isImage("clip.gif"));
        assertFalse(PhotoFilter.isImage("raw.cr2"));
        assertFalse(PhotoFilter.isImage("raw.nef"));
        assertFalse(PhotoFilter.isImage("video.mp4"));
        assertFalse(PhotoFilter.isImage("noextension"));
        assertFalse(PhotoFilter.isImage("trailingdot."));
        assertFalse(PhotoFilter.isImage(null));
        assertFalse(PhotoFilter.isImage(""));
    }

    @Test
    public void skipsServiceFolders() {
        assertTrue(PhotoFilter.isSkippedDir("@eaDir"));
        assertTrue(PhotoFilter.isSkippedDir("@Recycle"));
        assertTrue(PhotoFilter.isSkippedDir("#recycle"));
        assertTrue(PhotoFilter.isSkippedDir("$RECYCLE.BIN"));
        assertTrue(PhotoFilter.isSkippedDir("System Volume Information"));
        assertTrue(PhotoFilter.isSkippedDir(".thumbnails"));
        assertTrue(PhotoFilter.isSkippedDir("LOST.DIR"));
        assertFalse(PhotoFilter.isSkippedDir("Camera Uploads"));
        assertFalse(PhotoFilter.isSkippedDir("2026"));
    }

    @Test
    public void skipsServiceFiles() {
        assertTrue(PhotoFilter.isSkippedFile("Thumbs.db"));
        assertTrue(PhotoFilter.isSkippedFile(".DS_Store"));
        assertTrue(PhotoFilter.isSkippedFile("desktop.ini"));
        assertFalse(PhotoFilter.isSkippedFile("IMG_1.jpg"));
    }

    @Test
    public void extensionIsLowerCased() {
        assertEquals("jpg", PhotoFilter.extension("A.JPG"));
        assertEquals("", PhotoFilter.extension("A"));
    }
}
