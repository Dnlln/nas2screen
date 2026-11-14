package com.nas2screen.screensaver.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Infinite photo order: sequential or shuffled, never repeating an item twice in
 * a row (unless there is only a single item).
 */
public final class Playlist<T> {

    private final List<T> items;
    private final boolean shuffle;
    private final Random random;
    private final List<Integer> order = new ArrayList<>();
    private int cursor = -1;
    private int lastServed = -1;

    public Playlist(List<T> items, boolean shuffle, Random random) {
        this.items = items == null ? Collections.emptyList() : items;
        this.shuffle = shuffle;
        this.random = random == null ? new Random() : random;
        rebuild();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    public List<T> items() {
        return items;
    }

    public boolean isShuffled() {
        return shuffle;
    }

    public T next() {
        if (items.isEmpty()) {
            return null;
        }
        cursor++;
        if (cursor >= order.size()) {
            rebuild();
            cursor = 0;
        }
        lastServed = order.get(cursor);
        return items.get(lastServed);
    }

    /** Index (in the source list) of the item that {@link #next()} returned last. */
    public int lastIndex() {
        return lastServed;
    }

    private void rebuild() {
        order.clear();
        for (int i = 0; i < items.size(); i++) {
            order.add(i);
        }
        if (shuffle && order.size() > 1) {
            // Avoid the same photo on both sides of the cycle boundary.
            for (int attempt = 0; attempt < 8; attempt++) {
                Collections.shuffle(order, random);
                if (order.get(0) != lastServed) {
                    break;
                }
            }
            if (order.get(0) == lastServed) {
                Collections.swap(order, 0, random.nextInt(order.size()));
            }
        }
        cursor = -1;
    }
}
