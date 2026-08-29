package de.danoeh.antennapod.net.sync.serviceinterface;

import android.content.Context;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

import java.util.List;

public abstract class StateSyncCoordinator {
    private static StateSyncCoordinator instance = new NoOpStateSyncCoordinator();

    public static StateSyncCoordinator getInstance() {
        return instance;
    }

    public static void setInstance(StateSyncCoordinator instance) {
        StateSyncCoordinator.instance = instance;
    }

    public abstract void initialize(Context context);

    public abstract void recordSubscription(Feed feed, boolean subscribed, MutationOrigin origin);

    public abstract void indexFeed(Feed feed);

    public abstract void recordPlayback(FeedMedia media, PlaybackSyncIntent intent, MutationOrigin origin);

    public abstract void recordPlayed(List<FeedItem> items, int played, MutationOrigin origin);

    public abstract void recordQueue(List<FeedItem> queue, MutationOrigin origin);

    public abstract void configure(String serverUrl, String token);

    public abstract void clearConfiguration();

    public abstract boolean isConfigured();

    public abstract String getServerUrl();

    public abstract void sync();

    public abstract void syncImmediately();

    public abstract void syncIfNotSyncedRecently();

    private static final class NoOpStateSyncCoordinator extends StateSyncCoordinator {
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
            return false;
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
        }

        @Override
        public void syncIfNotSyncedRecently() {
        }
    }
}
