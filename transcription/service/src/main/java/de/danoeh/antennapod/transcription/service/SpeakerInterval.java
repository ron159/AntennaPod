package de.danoeh.antennapod.transcription.service;

public class SpeakerInterval {
    private final long startTimeMs;
    private final long endTimeMs;
    private final int speaker;

    public SpeakerInterval(long startTimeMs, long endTimeMs, int speaker) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.speaker = speaker;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    public int getSpeaker() {
        return speaker;
    }
}
