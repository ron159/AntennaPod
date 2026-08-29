package de.danoeh.antennapod.net.sync.statesync;

public final class HybridLogicalClock {
    private HlcTimestamp last;

    public HybridLogicalClock() {
        this(new HlcTimestamp(0, 0));
    }

    public HybridLogicalClock(HlcTimestamp initial) {
        last = initial;
    }

    public HlcTimestamp last() {
        return last;
    }

    public HlcTimestamp tick(long physicalTimeMilliseconds) {
        requirePhysicalTime(physicalTimeMilliseconds);
        if (physicalTimeMilliseconds > last.wallMilliseconds()) {
            last = new HlcTimestamp(physicalTimeMilliseconds, 0);
        } else {
            last = new HlcTimestamp(last.wallMilliseconds(), Math.incrementExact(last.counter()));
        }
        return last;
    }

    public HlcTimestamp observe(HlcTimestamp remote, long physicalTimeMilliseconds) {
        requirePhysicalTime(physicalTimeMilliseconds);
        long wall = Math.max(Math.max(last.wallMilliseconds(), remote.wallMilliseconds()),
                physicalTimeMilliseconds);
        long counter;
        if (wall == last.wallMilliseconds() && wall == remote.wallMilliseconds()) {
            counter = Math.incrementExact(Math.max(last.counter(), remote.counter()));
        } else if (wall == last.wallMilliseconds()) {
            counter = Math.incrementExact(last.counter());
        } else if (wall == remote.wallMilliseconds()) {
            counter = Math.incrementExact(remote.counter());
        } else {
            counter = 0;
        }
        last = new HlcTimestamp(wall, counter);
        return last;
    }

    private static void requirePhysicalTime(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Physical time must not be negative");
        }
    }
}
