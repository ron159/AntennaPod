package de.danoeh.antennapod.storage.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;

import java.util.List;

public final class RemoteStateSyncDao {
    public boolean applySubscription(String canonicalKey, String feedUrl, boolean subscribed,
                                     SyncAliasDao aliasDao) {
        Long feedId = aliasDao.localId("feed", canonicalKey);
        boolean inserted = false;
        if (feedId == null) {
            feedId = findFeedId(feedUrl);
        }
        if (feedId == null && subscribed) {
            ContentValues values = new ContentValues();
            values.put(PodDBAdapter.KEY_TITLE, feedUrl);
            values.put(PodDBAdapter.KEY_DOWNLOAD_URL, feedUrl);
            values.put(PodDBAdapter.KEY_STATE, Feed.STATE_SUBSCRIBED);
            feedId = database().insertOrThrow(PodDBAdapter.TABLE_NAME_FEEDS, null, values);
            inserted = true;
        }
        if (feedId != null) {
            ContentValues values = new ContentValues();
            values.put(PodDBAdapter.KEY_STATE,
                    subscribed ? Feed.STATE_SUBSCRIBED : Feed.STATE_NOT_SUBSCRIBED);
            database().update(PodDBAdapter.TABLE_NAME_FEEDS, values,
                    PodDBAdapter.KEY_ID + "=?", new String[]{String.valueOf(feedId)});
            aliasDao.linkLocal("feed", feedId, canonicalKey);
        }
        return inserted;
    }

    public boolean applyPlayback(String canonicalKey, long positionMilliseconds, Long durationMilliseconds,
                                 boolean played, SyncAliasDao aliasDao) {
        Long itemId = aliasDao.localId("episode", canonicalKey);
        if (itemId == null) {
            return false;
        }
        ContentValues itemValues = new ContentValues();
        itemValues.put(PodDBAdapter.KEY_READ, played ? FeedItem.PLAYED : FeedItem.UNPLAYED);
        database().update(PodDBAdapter.TABLE_NAME_FEED_ITEMS, itemValues,
                PodDBAdapter.KEY_ID + "=?", new String[]{String.valueOf(itemId)});

        ContentValues mediaValues = new ContentValues();
        mediaValues.put(PodDBAdapter.KEY_POSITION, positionMilliseconds);
        if (durationMilliseconds != null) {
            mediaValues.put(PodDBAdapter.KEY_DURATION, durationMilliseconds);
        }
        database().update(PodDBAdapter.TABLE_NAME_FEED_MEDIA, mediaValues,
                PodDBAdapter.KEY_FEEDITEM + "=?", new String[]{String.valueOf(itemId)});
        return true;
    }

    public void linkEpisodeIfKnown(String canonicalKey, String feedUrl, String guid, SyncAliasDao aliasDao) {
        if (feedUrl == null || guid == null) {
            return;
        }
        String query = "SELECT i." + PodDBAdapter.KEY_ID
                + " FROM " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " i"
                + " JOIN " + PodDBAdapter.TABLE_NAME_FEEDS + " f ON i." + PodDBAdapter.KEY_FEED
                + "=f." + PodDBAdapter.KEY_ID
                + " WHERE f." + PodDBAdapter.KEY_DOWNLOAD_URL + "=? AND i."
                + PodDBAdapter.KEY_ITEM_IDENTIFIER + "=? LIMIT 1";
        try (Cursor cursor = database().rawQuery(query, new String[]{feedUrl, guid})) {
            if (cursor.moveToFirst()) {
                aliasDao.linkLocal("episode", cursor.getLong(0), canonicalKey);
            }
        }
    }

    public void replaceQueue(List<Long> itemIds) {
        SQLiteDatabase database = database();
        database.delete(PodDBAdapter.TABLE_NAME_QUEUE, null, null);
        for (int index = 0; index < itemIds.size(); index++) {
            long itemId = itemIds.get(index);
            Long feedId = findItemFeedId(itemId);
            if (feedId == null) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(PodDBAdapter.KEY_ID, index);
            values.put(PodDBAdapter.KEY_FEEDITEM, itemId);
            values.put(PodDBAdapter.KEY_FEED, feedId);
            database.insertWithOnConflict(PodDBAdapter.TABLE_NAME_QUEUE, null,
                    values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private Long findFeedId(String feedUrl) {
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_FEEDS,
                new String[]{PodDBAdapter.KEY_ID}, PodDBAdapter.KEY_DOWNLOAD_URL + "=?",
                new String[]{feedUrl}, null, null, null, "1")) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private Long findItemFeedId(long itemId) {
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_FEED_ITEMS,
                new String[]{PodDBAdapter.KEY_FEED}, PodDBAdapter.KEY_ID + "=?",
                new String[]{String.valueOf(itemId)}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private SQLiteDatabase database() {
        return PodDBAdapter.getInstance().getDatabase();
    }
}
