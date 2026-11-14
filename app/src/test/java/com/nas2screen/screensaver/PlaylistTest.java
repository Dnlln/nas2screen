package com.nas2screen.screensaver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.nas2screen.screensaver.source.Playlist;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PlaylistTest {

    private static List<String> items(int count) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add("photo" + i);
        }
        return list;
    }

    @Test
    public void sequentialOrderCycles() {
        Playlist<String> playlist = new Playlist<>(items(3), false, new Random(1));
        assertEquals("photo0", playlist.next());
        assertEquals("photo1", playlist.next());
        assertEquals("photo2", playlist.next());
        assertEquals("photo0", playlist.next());
        assertEquals("photo1", playlist.next());
    }

    @Test
    public void shuffleNeverRepeatsConsecutively() {
        Playlist<String> playlist = new Playlist<>(items(4), true, new Random(42));
        String previous = null;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            String next = playlist.next();
            assertNotNull(next);
            assertFalse("two identical in a row: " + next, next.equals(previous));
            previous = next;
            seen.add(next);
        }
        assertEquals(4, seen.size());
    }

    @Test
    public void shuffleVisitsEveryItemOncePerCycle() {
        Playlist<String> playlist = new Playlist<>(items(6), true, new Random(7));
        for (int cycle = 0; cycle < 20; cycle++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 6; i++) {
                seen.add(playlist.next());
            }
            assertEquals("cycle " + cycle, 6, seen.size());
        }
    }

    @Test
    public void singleItemRepeats() {
        Playlist<String> playlist = new Playlist<>(items(1), true, new Random(3));
        assertEquals("photo0", playlist.next());
        assertEquals("photo0", playlist.next());
    }

    @Test
    public void emptyPlaylistIsNull() {
        Playlist<String> playlist = new Playlist<>(items(0), true, new Random(3));
        assertTrue(playlist.isEmpty());
        assertNull(playlist.next());
    }
}
