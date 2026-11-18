package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class StateSyncDatabaseTest {
    private SQLiteDatabase database;
    private SyncCursorDao cursorDao;
    private SyncOutboxDao outboxDao;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        database = PodDBAdapter.getInstance().getDatabase();
        cursorDao = new SyncCursorDao();
        outboxDao = new SyncOutboxDao();
    }

    @Test
    public void schemaContainsTransactionalSyncTables() {
        for (String table : List.of("SyncOutbox", "SyncCursor", "SyncAliases", "SyncEntityStates")) {
            try (Cursor cursor = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{table})) {
                assertTrue(table, cursor.moveToFirst());
            }
        }
    }

    @Test
    public void localClockAndSequenceRemainMonotonicAcrossRestarts() {
        cursorDao.initialize("android-test");
        SyncCursorDao.LocalVersion first = cursorDao.nextLocalVersion(1_000);
        SyncCursorDao.LocalVersion second = cursorDao.nextLocalVersion(900);

        assertEquals(1, first.localSequence());
        assertEquals(1_000, first.wallMilliseconds());
        assertEquals(0, first.counter());
        assertEquals(2, second.localSequence());
        assertEquals(1_000, second.wallMilliseconds());
        assertEquals(1, second.counter());
        assertEquals(second, cursorDao.current().localVersion());
    }

    @Test
    public void outboxCoalescesAnUnsentEntityWithoutReusingSequence() {
        cursorDao.initialize("android-test");
        SyncCursorDao.LocalVersion first = cursorDao.nextLocalVersion(1_000);
        outboxDao.put(first, "episode_playback", "episode-key", "{\"value\":1}");
        SyncCursorDao.LocalVersion second = cursorDao.nextLocalVersion(1_001);
        outboxDao.put(second, "episode_playback", "episode-key", "{\"value\":2}");

        List<SyncOutboxDao.Entry> entries = outboxDao.pending(500);
        assertEquals(1, entries.size());
        assertEquals(2, entries.get(0).localSequence());
        assertEquals("{\"value\":2}", entries.get(0).envelopeJson());
        assertEquals(2, cursorDao.current().lastLocalSequence());
    }

    @Test
    public void responseApplicationCanRollbackCursorAndAcknowledgementTogether() {
        cursorDao.initialize("android-test");
        SyncCursorDao.LocalVersion version = cursorDao.nextLocalVersion(1_000);
        outboxDao.put(version, "subscription", "feed-key", "{\"subscribed\":true}");

        database.beginTransactionNonExclusive();
        try {
            cursorDao.advanceServerSequence(12);
            outboxDao.deleteAccepted("android-test", 1);
        } finally {
            database.endTransaction();
        }

        assertEquals(0, cursorDao.current().serverSequence());
        assertEquals(1, outboxDao.pending(500).size());
    }

    @Test
    public void switchingServersClearsServerBoundStateButPreservesLocalIdentity() {
        cursorDao.initialize("android-test");
        SyncCursorDao.LocalVersion version = cursorDao.nextLocalVersion(1_000);
        cursorDao.advanceServerSequence(12);
        outboxDao.put(version, "subscription", "feed-key", "{\"subscribed\":true}");
        SyncEntityStateDao stateDao = new SyncEntityStateDao();
        stateDao.put(new SyncEntityStateDao.State("subscription", "feed-key",
                "{\"subscribed\":true}", 1_000, 0, "android-test", 0));

        StateSyncDatabase.runInTransaction(() -> {
            cursorDao.resetServerSequence();
            outboxDao.clear();
            stateDao.clear();
        });

        assertEquals(0, cursorDao.current().serverSequence());
        assertEquals(1, cursorDao.current().lastLocalSequence());
        assertEquals("android-test", cursorDao.current().deviceId());
        assertTrue(outboxDao.pending(500).isEmpty());
        assertTrue(stateDao.list("subscription").isEmpty());
    }

    @Test
    public void schemaRejectsUnknownSyncEntityTypes() {
        cursorDao.initialize("android-test");
        SyncCursorDao.LocalVersion version = cursorDao.nextLocalVersion(1_000);

        assertThrows(RuntimeException.class,
                () -> outboxDao.put(version, "unknown", "entity-key", "{}"));
        assertThrows(RuntimeException.class,
                () -> new SyncEntityStateDao().put(new SyncEntityStateDao.State(
                        "unknown", "entity-key", "{}", 1_000, 0, "android-test", 0)));
    }
}
