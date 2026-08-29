package de.danoeh.antennapod.net.sync.serviceinterface;

public enum PlaybackSyncIntent {
    CHECKPOINT("checkpoint"),
    PLAYBACK_PAUSE("playback_pause"),
    SEEK("seek"),
    MARK_PLAYED("mark_played"),
    MARK_UNPLAYED("mark_unplayed");

    private final String value;

    PlaybackSyncIntent(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
