package de.danoeh.antennapod.net.sync.statesync;

public record VersionStamp(HlcTimestamp timestamp, String deviceId) implements Comparable<VersionStamp> {
    public VersionStamp {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID must not be empty");
        }
        deviceId = deviceId.trim();
    }

    @Override
    public int compareTo(VersionStamp other) {
        int timestampComparison = timestamp.compareTo(other.timestamp);
        return timestampComparison != 0 ? timestampComparison : deviceId.compareTo(other.deviceId);
    }
}
