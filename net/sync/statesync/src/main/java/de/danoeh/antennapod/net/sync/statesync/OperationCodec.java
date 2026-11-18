package de.danoeh.antennapod.net.sync.statesync;

import org.json.JSONException;
import org.json.JSONObject;

public final class OperationCodec {
    public static final String SUBSCRIPTION = "subscription";
    public static final String EPISODE_PLAYBACK = "episode_playback";
    public static final String QUEUE_ENTRY = "queue_entry";

    private OperationCodec() {
    }

    public static JSONObject subscriptionPayload(String feedUrl, boolean subscribed) throws JSONException {
        return new JSONObject().put("feed_url", feedUrl).put("subscribed", subscribed);
    }

    public static JSONObject playbackPayload(String intent, long positionMilliseconds, Long durationMilliseconds,
                                             boolean played, String feedUrl, String guid) throws JSONException {
        if (positionMilliseconds < 0 || durationMilliseconds != null && durationMilliseconds < 0) {
            throw new IllegalArgumentException("Playback values must not be negative");
        }
        JSONObject payload = new JSONObject().put("intent", intent)
                .put("position_ms", positionMilliseconds).put("played", played);
        if (durationMilliseconds != null) {
            payload.put("duration_ms", durationMilliseconds);
        }
        if (feedUrl != null && !feedUrl.isBlank()) {
            payload.put("feed_url", feedUrl);
        }
        if (guid != null && !guid.isBlank()) {
            payload.put("guid", guid);
        }
        validatePayload(EPISODE_PLAYBACK, payload);
        return payload;
    }

    public static JSONObject queuePayload(FractionalIndex orderToken, boolean present) throws JSONException {
        return new JSONObject().put("order_token", orderToken.value()).put("present", present);
    }

    public static JSONObject encode(SyncMutation mutation) throws JSONException {
        return new JSONObject().put("local_seq", mutation.localSequence())
                .put("hlc", encode(mutation.hlc())).put("entity_type", mutation.entityType())
                .put("entity_key", mutation.entityKey()).put("payload", mutation.payload());
    }

    public static SyncMutation decodeMutation(JSONObject object) throws JSONException {
        return new SyncMutation(object.getLong("local_seq"), decodeHlc(object.getJSONObject("hlc")),
                object.getString("entity_type"), object.getString("entity_key"), object.getJSONObject("payload"));
    }

    public static JSONObject encode(HlcTimestamp timestamp) throws JSONException {
        return new JSONObject().put("wall_ms", timestamp.wallMilliseconds()).put("counter", timestamp.counter());
    }

    public static HlcTimestamp decodeHlc(JSONObject object) throws JSONException {
        return new HlcTimestamp(object.getLong("wall_ms"), object.getLong("counter"));
    }

    static void validatePayload(String entityType, JSONObject payload) {
        try {
            switch (entityType) {
                case SUBSCRIPTION -> {
                    payload.getString("feed_url");
                    payload.getBoolean("subscribed");
                }
                case EPISODE_PLAYBACK -> {
                    String intent = payload.getString("intent");
                    if (!intent.equals("checkpoint") && !intent.equals("playback_pause")
                            && !intent.equals("seek") && !intent.equals("mark_played")
                            && !intent.equals("mark_unplayed")) {
                        throw new IllegalArgumentException("Unknown playback intent");
                    }
                    if (payload.getLong("position_ms") < 0
                            || payload.has("duration_ms") && payload.getLong("duration_ms") < 0) {
                        throw new IllegalArgumentException("Playback values must not be negative");
                    }
                    payload.getBoolean("played");
                }
                case QUEUE_ENTRY -> {
                    new FractionalIndex(payload.getString("order_token"));
                    payload.getBoolean("present");
                }
                default -> throw new IllegalArgumentException("Unknown entity type");
            }
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid entity payload", exception);
        }
    }
}
