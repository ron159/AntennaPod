package de.danoeh.antennapod.net.sync.statesync;

import de.danoeh.antennapod.storage.database.SyncCursorDao;
import de.danoeh.antennapod.storage.database.SyncEntityStateDao;
import de.danoeh.antennapod.storage.database.SyncOutboxDao;

import org.json.JSONException;
import org.json.JSONObject;

final class MutationRecorder {
    private final SyncCursorDao cursorDao = new SyncCursorDao();
    private final SyncEntityStateDao stateDao = new SyncEntityStateDao();
    private final SyncOutboxDao outboxDao = new SyncOutboxDao();

    void record(String entityType, String entityKey, JSONObject payload) {
        SyncCursorDao.LocalVersion version = cursorDao.nextLocalVersion(System.currentTimeMillis());
        SyncMutation mutation = new SyncMutation(version.localSequence(),
                new HlcTimestamp(version.wallMilliseconds(), version.counter()), entityType, entityKey, payload);
        try {
            String encoded = OperationCodec.encode(mutation).toString();
            int priority = playbackPriority(entityType, payload);
            for (SyncOutboxDao.Entry entry : outboxDao.pendingForEntity(
                    version.deviceId(), entityType, entityKey)) {
                JSONObject existingEnvelope = new JSONObject(entry.envelopeJson());
                int existingPriority = playbackPriority(entityType,
                        OperationCodec.decodeMutation(existingEnvelope).payload());
                if (!entityType.equals(OperationCodec.EPISODE_PLAYBACK)
                        || priority > 0 || existingPriority == 0) {
                    outboxDao.delete(entry.deviceId(), entry.localSequence());
                }
            }
            stateDao.put(new SyncEntityStateDao.State(entityType, entityKey, payload.toString(),
                    version.wallMilliseconds(), version.counter(), version.deviceId(), priority));
            outboxDao.append(version, entityType, entityKey, encoded);
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not encode StateSync mutation", exception);
        }
    }

    private static int playbackPriority(String entityType, JSONObject payload) throws JSONException {
        if (!entityType.equals(OperationCodec.EPISODE_PLAYBACK)) {
            return 0;
        }
        return switch (payload.getString("intent")) {
            case "seek", "mark_played", "mark_unplayed" -> 1;
            default -> 0;
        };
    }
}
