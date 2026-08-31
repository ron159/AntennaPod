package de.danoeh.antennapod.transcription.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class GeneratedTranscriptJsonTest {
    @Test
    public void writesPodcastingTwoJsonWithSecondsAndSpeaker() throws Exception {
        GeneratedTranscript transcript = new GeneratedTranscript("sense-voice-int8", List.of(
                new GeneratedTranscript.Segment(1_250, 3_500, "你好", "Speaker 1"),
                new GeneratedTranscript.Segment(3_500, 5_000, "世界", "")));

        JSONObject json = new JSONObject(GeneratedTranscriptJson.write(transcript));
        JSONArray segments = json.getJSONArray("segments");

        assertEquals("1.0.0", json.getString("version"));
        assertEquals("sense-voice-int8", json.getJSONObject("generatedBy").getString("model"));
        assertEquals(1.25, segments.getJSONObject(0).getDouble("startTime"), 0.001);
        assertEquals(3.5, segments.getJSONObject(0).getDouble("endTime"), 0.001);
        assertEquals("Speaker 1", segments.getJSONObject(0).getString("speaker"));
        assertEquals("世界", segments.getJSONObject(1).getString("body"));
        assertEquals(false, segments.getJSONObject(1).has("speaker"));
    }
}
