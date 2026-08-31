package de.danoeh.antennapod.event;

public class TranscriptionEvent {
    public enum Type {
        MODEL_CHANGED,
        TRANSCRIPT_READY,
        AI_READY,
        FAILED
    }

    private final Type type;
    private final long mediaId;
    private final String value;

    public TranscriptionEvent(Type type, long mediaId, String value) {
        this.type = type;
        this.mediaId = mediaId;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public long getMediaId() {
        return mediaId;
    }

    public String getValue() {
        return value;
    }
}
