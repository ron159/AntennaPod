package de.danoeh.antennapod.net.sync.statesync;

import android.content.Context;

import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.database.SyncCursorDao;
import de.danoeh.antennapod.storage.database.SyncOutboxDao;

import org.json.JSONObject;
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
public class StateSyncRepositoryTest {
    private SyncCursorDao cursorDao;
    private SyncOutboxDao outboxDao;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        cursorDao = new SyncCursorDao();
        cursorDao.initialize("android-test");
        outboxDao = new SyncOutboxDao();
    }

    @Test
    public void appliesRemoteSubscriptionAndAdvancesCursorAtomically() throws Exception {
        String feedUrl = "https://example.com/feed.xml";
        SyncChange change = new SyncChange(1, "ios-test", new HlcTimestamp(1_000, 0),
                OperationCodec.SUBSCRIPTION, CanonicalIdentity.feedKey(feedUrl),
                OperationCodec.subscriptionPayload(feedUrl, true));

        new StateSyncRepository().apply(new SyncResponse(0, 1, List.of(change)), 0);

        assertEquals(1, cursorDao.current().serverSequence());
        assertTrue(DBReader.getFeedListDownloadUrls(false).contains(feedUrl));
    }

    @Test
    public void rejectsOverAcknowledgementWithoutDeletingOutbox() throws Exception {
        SyncCursorDao.LocalVersion version = cursorDao.nextLocalVersion(1_000);
        SyncMutation mutation = new SyncMutation(version.localSequence(), new HlcTimestamp(1_000, 0),
                OperationCodec.SUBSCRIPTION, "feed-key",
                OperationCodec.subscriptionPayload("https://example.com/feed.xml", true));
        outboxDao.put(version, mutation.entityType(), mutation.entityKey(),
                OperationCodec.encode(mutation).toString());

        assertThrows(IllegalArgumentException.class,
                () -> new StateSyncRepository().apply(new SyncResponse(2, 0, List.of()), 1));

        assertEquals(0, cursorDao.current().serverSequence());
        assertEquals(1, outboxDao.pending(500).size());
    }

    @Test
    public void preservesExplicitPlaybackIntentWhileCoalescingLaterCheckpoints() throws Exception {
        MutationRecorder recorder = new MutationRecorder();
        recorder.record(OperationCodec.EPISODE_PLAYBACK, "episode-key",
                OperationCodec.playbackPayload("seek", 1_000, 10_000L, false, null, null));
        recorder.record(OperationCodec.EPISODE_PLAYBACK, "episode-key",
                OperationCodec.playbackPayload("checkpoint", 2_000, 10_000L, false, null, null));
        recorder.record(OperationCodec.EPISODE_PLAYBACK, "episode-key",
                OperationCodec.playbackPayload("checkpoint", 3_000, 10_000L, false, null, null));

        List<SyncOutboxDao.Entry> pending = outboxDao.pending(500);
        assertEquals(2, pending.size());
        assertEquals("seek", OperationCodec.decodeMutation(
                new JSONObject(pending.get(0).envelopeJson())).payload().getString("intent"));
        assertEquals("checkpoint", OperationCodec.decodeMutation(
                new JSONObject(pending.get(1).envelopeJson())).payload().getString("intent"));
        assertEquals(3_000, OperationCodec.decodeMutation(
                new JSONObject(pending.get(1).envelopeJson())).payload().getLong("position_ms"));
    }
}
