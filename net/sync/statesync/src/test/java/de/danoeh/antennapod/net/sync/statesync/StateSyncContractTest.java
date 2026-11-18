package de.danoeh.antennapod.net.sync.statesync;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class StateSyncContractTest {
    @Test
    public void hybridClockNeverMovesBackwardAndObservesRemoteTime() {
        HybridLogicalClock clock = new HybridLogicalClock(new HlcTimestamp(100, 3));
        assertEquals(new HlcTimestamp(100, 4), clock.tick(90));
        assertEquals(new HlcTimestamp(120, 3), clock.observe(new HlcTimestamp(120, 2), 110));
        assertEquals(new HlcTimestamp(130, 0), clock.observe(new HlcTimestamp(110, 50), 130));
    }

    @Test
    public void fractionalIndexesMatchIosGoldenValues() {
        FractionalIndex initial = FractionalIndex.between(null, null);
        assertEquals("7fffffffffffffff", initial.value());
        assertEquals("3fffffffffffffff", FractionalIndex.between(null, initial).value());
        assertEquals("bfffffffffffffff", FractionalIndex.between(initial, null).value());
        assertEquals(List.of("3fffffffffffffff", "7ffffffffffffffe", "bffffffffffffffd"),
                FractionalIndex.rebase(3).stream().map(FractionalIndex::value).toList());
        assertThrows(IllegalArgumentException.class, () -> new FractionalIndex("0000000000000000"));
    }

    @Test
    public void playbackMutationMatchesIosWireFormat() throws Exception {
        JSONObject payload = OperationCodec.playbackPayload("playback_pause", 1_264_000L,
                3_600_000L, false, "https://example.com/feed.xml", "episode-guid");
        SyncMutation mutation = new SyncMutation(157, new HlcTimestamp(1_787_935_800_123L, 2),
                "episode_playback", "ep_6f1c", payload);

        JSONObject json = OperationCodec.encode(mutation);

        assertEquals(157, json.getLong("local_seq"));
        assertEquals(1_787_935_800_123L, json.getJSONObject("hlc").getLong("wall_ms"));
        assertEquals("episode_playback", json.getString("entity_type"));
        assertEquals("playback_pause", json.getJSONObject("payload").getString("intent"));
        assertFalse(json.getJSONObject("payload").has("episode_key"));
        assertEquals(mutation, OperationCodec.decodeMutation(json));
    }

    @Test
    public void conflictResolutionMatchesIosIntentAndVersionRules() throws Exception {
        JSONObject explicitSeek = OperationCodec.playbackPayload("seek", 600_000, 3_600_000L,
                false, null, null);
        JSONObject newerCheckpoint = OperationCodec.playbackPayload("checkpoint", 3_000_000,
                3_600_000L, false, null, null);
        VersionStamp current = new VersionStamp(new HlcTimestamp(100, 0), "ios");
        VersionStamp incoming = new VersionStamp(new HlcTimestamp(200, 0), "android");

        assertFalse(ConflictResolver.shouldApply("episode_playback", explicitSeek, current,
                newerCheckpoint, incoming));
        assertTrue(ConflictResolver.shouldApply("subscription", new JSONObject(), current,
                new JSONObject(), incoming));
        assertTrue(new VersionStamp(new HlcTimestamp(100, 2), "ios-phone")
                .compareTo(new VersionStamp(new HlcTimestamp(100, 2), "android-phone")) > 0);
    }
}
