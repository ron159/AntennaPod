package de.danoeh.antennapod.net.sync.statesync;

import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.MutationOrigin;
import de.danoeh.antennapod.net.sync.serviceinterface.PlaybackSyncIntent;
import de.danoeh.antennapod.net.sync.serviceinterface.StateSyncCoordinator;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.storage.database.StateSyncDatabase;
import de.danoeh.antennapod.storage.database.RemoteStateSyncDao;
import de.danoeh.antennapod.storage.database.SyncAliasDao;
import de.danoeh.antennapod.storage.database.SyncCursorDao;
import de.danoeh.antennapod.storage.database.SyncEntityStateDao;
import de.danoeh.antennapod.storage.database.SyncOutboxDao;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class StateSyncCoordinatorImpl extends StateSyncCoordinator {
    private static final String TAG = "StateSync";
    private static final String WORK_NAME = "StateSyncWork";
    private static final String PERIODIC_WORK_NAME = "StateSyncPeriodicWork";

    private Context context;
    private StateSyncSettingsStore settingsStore;
    private final SyncCursorDao cursorDao = new SyncCursorDao();
    private final SyncAliasDao aliasDao = new SyncAliasDao();
    private final SyncEntityStateDao stateDao = new SyncEntityStateDao();
    private final SyncOutboxDao outboxDao = new SyncOutboxDao();
    private final RemoteStateSyncDao remoteDao = new RemoteStateSyncDao();
    private final MutationRecorder recorder = new MutationRecorder();
    private boolean bootstrapping;
    private boolean configured;

    @Override
    public void initialize(Context context) {
        this.context = context.getApplicationContext();
        settingsStore = new StateSyncSettingsStore(this.context);
        if (!cursorDao.isInitialized()) {
            cursorDao.initialize("android-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
        }
        try {
            configured = settingsStore.load() != null;
        } catch (IllegalStateException exception) {
            configured = false;
        }
        if (configured && isActive()) {
            schedulePeriodicWork();
        }
    }

    @Override
    public void recordSubscription(Feed feed, boolean subscribed, MutationOrigin origin) {
        if (!shouldRecord(origin) || feed == null || feed.isLocalFeed()) {
            return;
        }
        try {
            String key = CanonicalIdentity.feedKey(feed.getDownloadUrl());
            aliasDao.linkLocal("feed", feed.getId(), key);
            recorder.record(OperationCodec.SUBSCRIPTION, key,
                    OperationCodec.subscriptionPayload(feed.getDownloadUrl(), subscribed));
            sync();
        } catch (IllegalArgumentException | JSONException exception) {
            Log.w(TAG, "Skipping a subscription that has no canonical StateSync identity");
        }
    }

    @Override
    public void indexFeed(Feed feed) {
        if (!isActive() || !isConfigured() || feed == null || feed.isLocalFeed()) {
            return;
        }
        try {
            String feedKey = CanonicalIdentity.feedKey(feed.getDownloadUrl());
            aliasDao.linkLocal("feed", feed.getId(), feedKey);
            if (feed.getItems() == null) {
                return;
            }
            for (FeedItem item : feed.getItems()) {
                String episodeKey = episodeKey(item);
                aliasDao.linkLocal("episode", item.getId(), episodeKey);
                applyStoredPlayback(item, episodeKey);
            }
            rebuildKnownQueue();
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Skipping feed entities that have no canonical StateSync identity");
        }
    }

    @Override
    public void recordPlayback(FeedMedia media, PlaybackSyncIntent intent, MutationOrigin origin) {
        if (!shouldRecord(origin) || media == null || media.getItem() == null) {
            return;
        }
        recordPlaybackItem(media.getItem(), intent);
        sync();
    }

    @Override
    public void recordPlayed(List<FeedItem> items, int played, MutationOrigin origin) {
        if (!shouldRecord(origin)) {
            return;
        }
        PlaybackSyncIntent intent = played == FeedItem.PLAYED
                ? PlaybackSyncIntent.MARK_PLAYED : PlaybackSyncIntent.MARK_UNPLAYED;
        for (FeedItem item : items) {
            if (item.hasMedia()) {
                recordPlaybackItem(item, intent);
            }
        }
        sync();
    }

    @Override
    public void recordQueue(List<FeedItem> queue, MutationOrigin origin) {
        if (!shouldRecord(origin)) {
            return;
        }
        try {
            List<FractionalIndex> tokens = FractionalIndex.rebase(queue.size());
            Set<String> present = new HashSet<>();
            for (int index = 0; index < queue.size(); index++) {
                FeedItem item = queue.get(index);
                String key = episodeKey(item);
                present.add(key);
                aliasDao.linkLocal("episode", item.getId(), key);
                recorder.record(OperationCodec.QUEUE_ENTRY, key,
                        OperationCodec.queuePayload(tokens.get(index), true));
            }
            for (SyncEntityStateDao.State state : stateDao.list(OperationCodec.QUEUE_ENTRY)) {
                if (present.contains(state.entityKey())) {
                    continue;
                }
                JSONObject current = new JSONObject(state.payloadJson());
                if (current.getBoolean("present")) {
                    recorder.record(OperationCodec.QUEUE_ENTRY, state.entityKey(),
                            OperationCodec.queuePayload(new FractionalIndex(
                                    current.getString("order_token")), false));
                }
            }
            sync();
        } catch (IllegalArgumentException | JSONException exception) {
            Log.w(TAG, "Skipping queue entities that have no canonical StateSync identity");
        }
    }

    @Override
    public void configure(String serverUrl, String token) {
        StateSyncConfiguration configuration = new StateSyncConfiguration(serverUrl, token);
        String boundServerUrl = settingsStore.boundServerUrl();
        boolean serverChanged = !boundServerUrl.isEmpty() && !boundServerUrl.equals(configuration.serverUrl());
        settingsStore.save(configuration, boundServerUrl.isEmpty() || serverChanged);
        if (serverChanged) {
            StateSyncDatabase.runInTransaction(() -> {
                cursorDao.resetServerSequence();
                outboxDao.clear();
                stateDao.clear();
            });
        }
        configured = true;
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.STATE_SYNC.getIdentifier());
        schedulePeriodicWork();
        syncImmediately();
    }

    @Override
    public void clearConfiguration() {
        settingsStore.clear();
        configured = false;
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String getServerUrl() {
        return settingsStore.serverUrl();
    }

    @Override
    public long getPendingChangeCount() {
        return outboxDao.count();
    }

    @Override
    public long getServerCursor() {
        return cursorDao.current().serverSequence();
    }

    @Override
    public long getLastSuccessfulSyncTime() {
        return SynchronizationSettings.getLastSuccessfulSyncTime();
    }

    @Override
    public void sync() {
        enqueue(3, TimeUnit.SECONDS, ExistingWorkPolicy.REPLACE);
    }

    @Override
    public void syncImmediately() {
        enqueue(0, TimeUnit.SECONDS, ExistingWorkPolicy.REPLACE);
    }

    @Override
    public void syncIfNotSyncedRecently() {
        if (System.currentTimeMillis() - SynchronizationSettings.getLastSyncAttempt() > 10 * 60 * 1000L) {
            syncImmediately();
        }
    }

    void bootstrap(List<Feed> feeds, List<FeedItem> queue) {
        if (settingsStore.isBootstrapped()) {
            return;
        }
        StateSyncDatabase.runInTransaction(() -> {
            bootstrapping = true;
            try {
                for (Feed feed : feeds) {
                    indexFeed(feed);
                    recordSubscription(feed, feed.getState() == Feed.STATE_SUBSCRIBED,
                            MutationOrigin.LOCAL_AUTOMATION);
                    if (feed.getItems() != null) {
                        for (FeedItem item : feed.getItems()) {
                            if (item.hasMedia() && (item.isPlayed() || item.getMedia().getPosition() > 0)) {
                                recordPlaybackItem(item, PlaybackSyncIntent.CHECKPOINT);
                            }
                        }
                    }
                }
                recordQueue(queue, MutationOrigin.LOCAL_AUTOMATION);
            } finally {
                bootstrapping = false;
            }
        });
        settingsStore.setBootstrapped();
    }

    StateSyncConfiguration configuration() {
        return settingsStore.load();
    }

    void syncNextBatch() {
        enqueue(0, TimeUnit.SECONDS, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    private void recordPlaybackItem(FeedItem item, PlaybackSyncIntent intent) {
        if (!item.hasMedia() || item.getFeed() == null || item.getFeed().isLocalFeed()) {
            return;
        }
        try {
            String key = episodeKey(item);
            FeedMedia media = item.getMedia();
            aliasDao.linkLocal("episode", item.getId(), key);
            Long duration = media.getDuration() >= 0 ? (long) media.getDuration() : null;
            recorder.record(OperationCodec.EPISODE_PLAYBACK, key,
                    OperationCodec.playbackPayload(intent.value(), Math.max(0, media.getPosition()), duration,
                            item.isPlayed(), item.getFeed().getDownloadUrl(), item.getItemIdentifier()));
        } catch (IllegalArgumentException | JSONException exception) {
            Log.w(TAG, "Skipping an episode that has no canonical StateSync identity");
        }
    }

    private String episodeKey(FeedItem item) {
        FeedMedia media = item.getMedia();
        return CanonicalIdentity.episodeKey(item.getFeed().getDownloadUrl(), item.getItemIdentifier(),
                media == null ? null : media.getDownloadUrl(), item.getLink(),
                item.getPubDate() == null ? null : item.getPubDate().getTime(), item.getTitle(),
                media == null ? null : (long) Math.max(0, media.getDuration()));
    }

    private void applyStoredPlayback(FeedItem item, String episodeKey) {
        SyncEntityStateDao.State state = stateDao.get(OperationCodec.EPISODE_PLAYBACK, episodeKey);
        if (state == null || !item.hasMedia()) {
            return;
        }
        try {
            JSONObject payload = new JSONObject(state.payloadJson());
            item.getMedia().setPosition((int) Math.min(Integer.MAX_VALUE, payload.getLong("position_ms")));
            if (payload.has("duration_ms")) {
                item.getMedia().setDuration((int) Math.min(Integer.MAX_VALUE, payload.getLong("duration_ms")));
            }
            item.setPlayed(payload.getBoolean("played"));
            remoteDao.applyPlayback(episodeKey, payload.getLong("position_ms"),
                    payload.has("duration_ms") ? payload.getLong("duration_ms") : null,
                    payload.getBoolean("played"), aliasDao);
        } catch (JSONException exception) {
            throw new IllegalStateException("Stored StateSync playback state is corrupt", exception);
        }
    }

    private void rebuildKnownQueue() {
        if (stateDao.list(OperationCodec.QUEUE_ENTRY).isEmpty()) {
            return;
        }
        List<QueueState> queue = new ArrayList<>();
        for (SyncEntityStateDao.State state : stateDao.list(OperationCodec.QUEUE_ENTRY)) {
            try {
                JSONObject payload = new JSONObject(state.payloadJson());
                Long itemId = aliasDao.localId("episode", state.entityKey());
                if (payload.getBoolean("present") && itemId != null) {
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

    private boolean shouldRecord(MutationOrigin origin) {
        return isActive() && isConfigured()
                && origin != MutationOrigin.REMOTE_SYNC && origin != MutationOrigin.MIGRATION;
    }

    private boolean isActive() {
        return SynchronizationProvider.STATE_SYNC.getIdentifier()
                .equals(SynchronizationSettings.getSelectedSyncProviderKey());
    }

    private void enqueue(long delay, TimeUnit unit, ExistingWorkPolicy policy) {
        if (bootstrapping || !isActive() || !isConfigured()) {
            return;
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(StateSyncWorker.class)
                .setConstraints(networkConstraints())
                .setInitialDelay(delay, unit)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request);
    }

    private Constraints networkConstraints() {
        return new Constraints.Builder().setRequiredNetworkType(UserPreferences.isAllowMobileSync()
                ? NetworkType.CONNECTED : NetworkType.UNMETERED).build();
    }

    private void schedulePeriodicWork() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(StateSyncWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(networkConstraints()).build());
    }

    private record QueueState(long itemId, String orderToken, String entityKey) {
    }
}
