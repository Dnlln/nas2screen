package com.nas2screen.screensaver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nas2screen.screensaver.net.SmbPath;

import org.junit.Test;

public class SmbPathTest {

    @Test
    public void splitsShareAndFolder() {
        SmbPath path = SmbPath.parse("192.168.88.23",
                "Multimedia/Camera Uploads/LMR-LX9/2026/02");
        assertTrue(path.isComplete());
        assertEquals("192.168.88.23", path.host);
        assertEquals("Multimedia", path.share);
        assertEquals("Camera Uploads\\LMR-LX9\\2026\\02", path.path);
        assertEquals("\\\\192.168.88.23\\Multimedia\\Camera Uploads\\LMR-LX9\\2026\\02",
                path.unc());
    }

    @Test
    public void acceptsFullUncInFolderField() {
        SmbPath path = SmbPath.parse("",
                "\\\\192.168.88.23\\Multimedia\\Camera Uploads\\2026\\02");
        assertTrue(path.isComplete());
        assertEquals("192.168.88.23", path.host);
        assertEquals("Multimedia", path.share);
        assertEquals("Camera Uploads\\2026\\02", path.path);
    }

    @Test
    public void doesNotDuplicateHostFromBothFields() {
        SmbPath path = SmbPath.parse("192.168.88.23",
                "\\\\192.168.88.23\\Multimedia\\Photos");
        assertEquals("192.168.88.23", path.host);
        assertEquals("Multimedia", path.share);
        assertEquals("Photos", path.path);
    }

    @Test
    public void acceptsSmbUrl() {
        SmbPath path = SmbPath.parse("nas.local", "smb://nas.local/Multimedia/Photos");
        assertEquals("nas.local", path.host);
        assertEquals("Multimedia", path.share);
        assertEquals("Photos", path.path);
    }

    @Test
    public void stripsPortAndTrailingSlashes() {
        SmbPath path = SmbPath.parse("192.168.88.23:445", "/Multimedia/");
        assertEquals("192.168.88.23", path.host);
        assertEquals("Multimedia", path.share);
        assertEquals("", path.path);
        assertEquals("Multimedia", path.relativeFolder());
    }

    @Test
    public void shareRootIsComplete() {
        SmbPath path = SmbPath.parse("nas", "Multimedia");
        assertTrue(path.isComplete());
        assertEquals("", path.sharePath());
        assertEquals("Multimedia", path.relativeFolder());
    }

    @Test
    public void ignoresDotSegments() {
        SmbPath path = SmbPath.parse("nas", "Multimedia/./Photos/../Photos2");
        assertEquals("Multimedia", path.share);
        assertEquals("Photos\\Photos2", path.path);
    }

    @Test
    public void incompleteWhenNothingGiven() {
        assertFalse(SmbPath.parse("", "").isComplete());
        assertFalse(SmbPath.parse("192.168.88.23", "").isComplete());
        assertFalse(SmbPath.parse("", "Multimedia").isComplete());
    }

    @Test
    public void normalizeFieldsFillsBothFields() {
        String[] fields = SmbPath.normalizeFields("", "\\\\192.168.88.23\\Multimedia\\a\\b");
        assertEquals("192.168.88.23", fields[0]);
        assertEquals("Multimedia\\a\\b", fields[1]);

        String[] unchanged = SmbPath.normalizeFields("192.168.88.23", "Multimedia/a");
        assertEquals("192.168.88.23", unchanged[0]);
        assertEquals("Multimedia\\a", unchanged[1]);
    }
}
