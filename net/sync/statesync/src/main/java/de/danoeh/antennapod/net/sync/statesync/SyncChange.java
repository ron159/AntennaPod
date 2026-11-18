package de.danoeh.antennapod.net.sync.statesync;

import org.json.JSONException;
import org.json.JSONObject;

public record SyncChange(long serverSequence, String deviceId, HlcTimestamp hlc,
                         String entityType, String entityKey, JSONObject payload) {
    public SyncChange {
        if (serverSequence < 0 || deviceId == null || deviceId.isBlank()
                || entityKey == null || entityKey.isBlank()) {
            throw new IllegalArgumentException("Invalid StateSync change envelope");
        }
        OperationCodec.validatePayload(entityType, payload);
    }

    public static SyncChange decode(JSONObject object) throws JSONException {
        return new SyncChange(object.getLong("server_seq"), object.getString("device_id"),
                OperationCodec.decodeHlc(object.getJSONObject("hlc")), object.getString("entity_type"),
                object.getString("entity_key"), object.getJSONObject("payload"));
    }
}
