package de.danoeh.antennapod.net.sync.statesync;

import org.json.JSONObject;

import java.util.Objects;

public final class SyncMutation {
    private final long localSequence;
    private final HlcTimestamp hlc;
    private final String entityType;
    private final String entityKey;
    private final JSONObject payload;

    public SyncMutation(long localSequence, HlcTimestamp hlc, String entityType,
                        String entityKey, JSONObject payload) {
        if (localSequence < 0 || entityKey == null || entityKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid mutation envelope");
        }
        OperationCodec.validatePayload(entityType, payload);
        this.localSequence = localSequence;
        this.hlc = hlc;
        this.entityType = entityType;
        this.entityKey = entityKey;
        this.payload = payload;
    }

    public long localSequence() {
        return localSequence;
    }

    public HlcTimestamp hlc() {
        return hlc;
    }

    public String entityType() {
        return entityType;
    }

    public String entityKey() {
        return entityKey;
    }

    public JSONObject payload() {
        return payload;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SyncMutation mutation)) {
            return false;
        }
        return localSequence == mutation.localSequence && hlc.equals(mutation.hlc)
                && entityType.equals(mutation.entityType) && entityKey.equals(mutation.entityKey)
                && payload.toString().equals(mutation.payload.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(localSequence, hlc, entityType, entityKey, payload.toString());
    }
}
