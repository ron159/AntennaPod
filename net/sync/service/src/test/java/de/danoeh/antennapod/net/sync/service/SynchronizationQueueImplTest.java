package de.danoeh.antennapod.net.sync.service;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.MutationOrigin;
import de.danoeh.antennapod.net.sync.serviceinterface.PlaybackSyncIntent;
import de.danoeh.antennapod.net.sync.serviceinterface.StateSyncCoordinator;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;

import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SynchronizationQueueImplTest {
    private StateSyncCoordinator originalCoordinator;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SynchronizationSettings.init(context);
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.STATE_SYNC.getIdentifier());
        originalCoordinator = StateSyncCoordinator.getInstance();
        EventBus.getDefault().removeAllStickyEvents();
    }

    @After
    public void tearDown() {
        StateSyncCoordinator.setInstance(originalCoordinator);
        EventBus.getDefault().removeAllStickyEvents();
    }

    @Test
    public void immediateStateSyncPublishesStartedStatusBeforeRunning() {
        RecordingStateSyncCoordinator coordinator = new RecordingStateSyncCoordinator();
        StateSyncCoordinator.setInstance(coordinator);

        new SynchronizationQueueImpl(ApplicationProvider.getApplicationContext()).syncImmediately();

        assertTrue(coordinator.syncImmediatelyCalled);
        SyncServiceEvent status = EventBus.getDefault().getStickyEvent(SyncServiceEvent.class);
        assertNotNull(status);
        assertEquals(R.string.sync_status_started, status.getMessageResId());
    }

    private static class RecordingStateSyncCoordinator extends StateSyncCoordinator {
        private boolean syncImmediatelyCalled;

        @Override
        public void initialize(Context context) {
        }

        @Override
        public void recordSubscription(Feed feed, boolean subscribed, MutationOrigin origin) {
        }

        @Override
        public void indexFeed(Feed feed) {
        }

        @Override
        public void recordPlayback(FeedMedia media, PlaybackSyncIntent intent, MutationOrigin origin) {
        }

        @Override
        public void recordPlayed(List<FeedItem> items, int played, MutationOrigin origin) {
        }

        @Override
        public void recordQueue(List<FeedItem> queue, MutationOrigin origin) {
        }

        @Override
        public void configure(String serverUrl, String token) {
        }

        @Override
        public void clearConfiguration() {
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String getServerUrl() {
            return "";
        }

        @Override
        public void sync() {
        }

        @Override
        public void syncImmediately() {
            syncImmediatelyCalled = true;
        }

        @Override
        public void syncIfNotSyncedRecently() {
        }
    }
}
