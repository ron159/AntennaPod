package de.danoeh.antennapod.net.sync.statesync;

import de.danoeh.antennapod.storage.database.RemoteStateSyncDao;
import de.danoeh.antennapod.storage.database.StateSyncDatabase;
import de.danoeh.antennapod.storage.database.SyncAliasDao;
import de.danoeh.antennapod.storage.database.SyncCursorDao;
import de.danoeh.antennapod.storage.database.SyncEntityStateDao;
import de.danoeh.antennapod.storage.database.SyncOutboxDao;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StateSyncRepository {
    static final int MAXIMUM_BODY_BYTES = 1024 * 1024;

    private final SyncCursorDao cursorDao = new SyncCursorDao();
    private final SyncOutboxDao outboxDao = new SyncOutboxDao();
    private final SyncAliasDao aliasDao = new SyncAliasDao();
    private final SyncEntityStateDao stateDao = new SyncEntityStateDao();
    private final RemoteStateSyncDao remoteDao = new RemoteStateSyncDao();

    RequestBatch requestBatch() throws JSONException {
        SyncCursorDao.CursorState cursor = cursorDao.current();
        List<SyncOutboxDao.Entry> pending = outboxDao.pending(500);
        JSONArray accepted = new JSONArray();
        long maximumAcknowledgement = cursor.lastLocalSequence();
        for (SyncOutboxDao.Entry entry : pending) {
            JSONObject envelope = new JSONObject(entry.envelopeJson());
            validateOutboxEntry(entry, envelope);
            accepted.put(envelope);
            JSONObject request = request(cursor, accepted);
            if (request.toString().getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BODY_BYTES) {
                accepted.remove(accepted.length() - 1);
                if (accepted.length() == 0) {
                    throw new IllegalStateException("StateSync mutation exceeds the request size limit");
                }
                break;
            }
            maximumAcknowledgement = entry.localSequence();
        }
        JSONObject request = request(cursor, accepted);
        return new RequestBatch(request.toString(), maximumAcknowledgement, accepted.length());
    }

    ApplyResult apply(SyncResponse response, long maximumAcknowledgement) {
        SyncCursorDao.CursorState cursor = cursorDao.current();
        validateResponse(response, cursor, maximumAcknowledgement);
        ApplyAccumulator accumulator = new ApplyAccumulator();
        StateSyncDatabase.runInTransaction(() -> {
            for (SyncChange change : response.changes()) {
                if (shouldApply(change)) {
                    applyBusinessState(change, accumulator);
                    stateDao.put(new SyncEntityStateDao.State(change.entityType(), change.entityKey(),
                            change.payload().toString(), change.hlc().wallMilliseconds(), change.hlc().counter(),
                            change.deviceId(), playbackPriority(change.entityType(), change.payload())));
                }
                cursorDao.observeRemote(change.hlc().wallMilliseconds(), change.hlc().counter());
            }
            if (accumulator.queueChanged) {
                rebuildQueue();
            }
            cursorDao.advanceServerSequence(response.nextCursor());
            outboxDao.deleteAccepted(cursor.deviceId(), response.acceptedLocalSequence());
        });
        return new ApplyResult(accumulator.hasNewSubscription, response.changes().size());
    }

    boolean hasPendingMutations() {
        return !outboxDao.pending(1).isEmpty();
    }

    private JSONObject request(SyncCursorDao.CursorState cursor, JSONArray mutations) throws JSONException {
        return new JSONObject().put("device_id", cursor.deviceId())
                .put("cursor", cursor.serverSequence()).put("mutations", mutations);
    }

    private void validateOutboxEntry(SyncOutboxDao.Entry entry, JSONObject envelope) throws JSONException {
        SyncMutation mutation = OperationCodec.decodeMutation(envelope);
        if (mutation.localSequence() != entry.localSequence()
                || !mutation.entityType().equals(entry.entityType())
                || !mutation.entityKey().equals(entry.entityKey())) {
            throw new IllegalStateException("StateSync outbox metadata does not match its envelope");
        }
    }

    private void validateResponse(SyncResponse response, SyncCursorDao.CursorState cursor,
                                  long maximumAcknowledgement) {
        if (response.acceptedLocalSequence() > maximumAcknowledgement
                || response.acceptedLocalSequence() > cursor.lastLocalSequence()
                || response.nextCursor() < cursor.serverSequence()) {
            throw new IllegalArgumentException("Invalid StateSync response acknowledgement or cursor");
        }
        long previous = cursor.serverSequence();
        for (SyncChange change : response.changes()) {
            if (change.serverSequence() <= previous || change.serverSequence() > response.nextCursor()) {
                throw new IllegalArgumentException("StateSync changes are not strictly ordered within the cursor");
            }
            previous = change.serverSequence();
        }
    }

    private boolean shouldApply(SyncChange change) {
        SyncEntityStateDao.State current = stateDao.get(change.entityType(), change.entityKey());
        if (current == null) {
            return true;
        }
        try {
            return ConflictResolver.shouldApply(change.entityType(), new JSONObject(current.payloadJson()),
                    new VersionStamp(new HlcTimestamp(current.wallMilliseconds(), current.counter()),
                            current.deviceId()), change.payload(), new VersionStamp(change.hlc(), change.deviceId()));
        } catch (JSONException exception) {
            throw new IllegalStateException("Stored StateSync state is corrupt", exception);
        }
    }

    private void applyBusinessState(SyncChange change, ApplyAccumulator accumulator) {
        try {
            JSONObject payload = change.payload();
            switch (change.entityType()) {
                case OperationCodec.SUBSCRIPTION -> {
                    String feedUrl = payload.getString("feed_url");
                    if (!CanonicalIdentity.feedKey(feedUrl).equals(change.entityKey())) {
                        throw new IllegalArgumentException("StateSync subscription key does not match its URL");
                    }
                    boolean inserted = remoteDao.applySubscription(change.entityKey(), feedUrl,
                            payload.getBoolean("subscribed"), aliasDao);
                    accumulator.hasNewSubscription |= inserted;
                }
                case OperationCodec.EPISODE_PLAYBACK -> {
                    String feedUrl = payload.optString("feed_url", null);
                    String guid = payload.optString("guid", null);
                    if (feedUrl != null && guid != null && !CanonicalIdentity.episodeKey(
                            feedUrl, guid, null, null, null, null, null).equals(change.entityKey())) {
                        throw new IllegalArgumentException("StateSync episode key does not match its locator");
                    }
                    remoteDao.linkEpisodeIfKnown(change.entityKey(), feedUrl, guid, aliasDao);
                    Long duration = payload.has("duration_ms") ? payload.getLong("duration_ms") : null;
                    remoteDao.applyPlayback(change.entityKey(), payload.getLong("position_ms"), duration,
                            payload.getBoolean("played"), aliasDao);
                }
                case OperationCodec.QUEUE_ENTRY -> accumulator.queueChanged = true;
                default -> throw new IllegalArgumentException("Unknown StateSync entity type");
            }
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid StateSync payload", exception);
        }
    }

    private void rebuildQueue() {
        List<QueueState> queue = new ArrayList<>();
        for (SyncEntityStateDao.State state : stateDao.list(OperationCodec.QUEUE_ENTRY)) {
            try {
                JSONObject payload = new JSONObject(state.payloadJson());
                if (!payload.getBoolean("present")) {
                    continue;
                }
                Long itemId = aliasDao.localId("episode", state.entityKey());
                if (itemId != null) {
                    queue.add(new QueueState(itemId, payload.getString("order_token"), state.entityKey()));
                }
            } catch (JSONException exception) {
                throw new IllegalStateException("Stored StateSync queue state is corrupt", exception);
            }
        }
        Collections.sort(queue, (left, right) -> {
            int tokenComparison = left.orderToken().compareTo(right.orderToken());
            return tokenComparison != 0 ? tokenComparison : left.entityKey().compareTo(right.entityKey());
        });
        List<Long> itemIds = new ArrayList<>(queue.size());
        for (QueueState state : queue) {
            itemIds.add(state.itemId());
        }
        remoteDao.replaceQueue(itemIds);
    }

    private static int playbackPriority(String entityType, JSONObject payload) {
        if (!entityType.equals(OperationCodec.EPISODE_PLAYBACK)) {
            return 0;
        }
        String intent = payload.optString("intent");
        return intent.equals("seek") || intent.equals("mark_played") || intent.equals("mark_unplayed") ? 1 : 0;
    }

    record RequestBatch(String json, long maximumAcknowledgement, int mutationCount) {
    }

    record ApplyResult(boolean hasNewSubscription, int receivedChangeCount) {
    }

    private record QueueState(long itemId, String orderToken, String entityKey) {
    }

    private static final class ApplyAccumulator {
        private boolean hasNewSubscription;
        private boolean queueChanged;
    }
}
