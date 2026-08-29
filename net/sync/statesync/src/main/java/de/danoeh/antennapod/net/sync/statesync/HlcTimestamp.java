package de.danoeh.antennapod.net.sync.statesync;

public record HlcTimestamp(long wallMilliseconds, long counter) implements Comparable<HlcTimestamp> {
    public HlcTimestamp {
        if (wallMilliseconds < 0 || counter < 0) {
            throw new IllegalArgumentException("HLC values must not be negative");
        }
    }

    @Override
    public int compareTo(HlcTimestamp other) {
        int wallComparison = Long.compare(wallMilliseconds, other.wallMilliseconds);
        return wallComparison != 0 ? wallComparison : Long.compare(counter, other.counter);
    }
}
