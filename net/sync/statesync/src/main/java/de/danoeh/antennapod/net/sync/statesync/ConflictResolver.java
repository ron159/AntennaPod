package de.danoeh.antennapod.net.sync.statesync;

import org.json.JSONException;
import org.json.JSONObject;

public final class ConflictResolver {
    private ConflictResolver() {
    }

    public static boolean shouldApply(String entityType, JSONObject currentPayload, VersionStamp currentVersion,
                                      JSONObject incomingPayload, VersionStamp incomingVersion) {
        if (entityType.equals(OperationCodec.EPISODE_PLAYBACK)) {
            int currentPriority = playbackPriority(currentPayload);
            int incomingPriority = playbackPriority(incomingPayload);
            if (currentPriority != incomingPriority) {
                return incomingPriority > currentPriority;
            }
        }
        return incomingVersion.compareTo(currentVersion) > 0;
    }

    private static int playbackPriority(JSONObject payload) {
        try {
            return switch (payload.getString("intent")) {
                case "seek", "mark_played", "mark_unplayed" -> 1;
                case "checkpoint", "playback_pause" -> 0;
                default -> throw new IllegalArgumentException("Unknown playback intent");
            };
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid playback payload", exception);
        }
    }
}
