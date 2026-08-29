package de.danoeh.antennapod.net.sync.statesync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.net.sync.serviceinterface.StateSyncCoordinator;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StateSyncWorker extends Worker {
    private static final String TAG = "StateSync";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    public StateSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!SynchronizationProvider.STATE_SYNC.getIdentifier()
                .equals(SynchronizationSettings.getSelectedSyncProviderKey())) {
            return Result.success();
        }
        if (!ACTIVE.compareAndSet(false, true)) {
            return Result.retry();
        }
        SynchronizationSettings.updateLastSynchronizationAttempt();
        EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_started));
        try {
            StateSyncCoordinator coordinator = StateSyncCoordinator.getInstance();
            if (!(coordinator instanceof StateSyncCoordinatorImpl implementation)) {
                return Result.failure();
            }
            StateSyncConfiguration configuration = implementation.configuration();
            if (configuration == null) {
                return Result.failure();
            }
            bootstrap(implementation);
            StateSyncRepository repository = new StateSyncRepository();
            StateSyncRepository.RequestBatch batch = repository.requestBatch();
            SyncResponse response = new StateSyncClient().synchronize(batch.json(), configuration);
            StateSyncRepository.ApplyResult result = repository.apply(response, batch.maximumAcknowledgement());
            if (result.hasNewSubscription()) {
                FeedUpdateManager.getInstance().runOnce(getApplicationContext());
            }
            EventBus.getDefault().post(new FeedListUpdateEvent(0));
            EventBus.getDefault().post(new FeedItemEvent(Collections.emptyList(), true));
            EventBus.getDefault().post(QueueEvent.setQueue(DBReader.getQueue()));
            if (repository.hasPendingMutations()) {
                implementation.syncNextBatch();
            }
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(true);
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_success));
            return Result.success();
        } catch (StateSyncHttpException exception) {
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(false);
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_error));
            if (exception.reason() == StateSyncHttpException.Reason.SERVER) {
                return Result.retry();
            }
            Log.e(TAG, exception.getMessage());
            return Result.failure();
        } catch (IOException exception) {
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(false);
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_error));
            return Result.retry();
        } catch (JSONException | IllegalArgumentException | IllegalStateException exception) {
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(false);
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_error));
            Log.e(TAG, exception.getMessage());
            return Result.failure();
        } finally {
            ACTIVE.set(false);
        }
    }

    private void bootstrap(StateSyncCoordinatorImpl coordinator) {
        List<Feed> feeds = DBReader.getFeedList();
        for (Feed feed : feeds) {
            DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        }
        List<FeedItem> queue = DBReader.getQueue();
        coordinator.bootstrap(feeds, queue);
    }
}
