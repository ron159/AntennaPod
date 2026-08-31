package de.danoeh.antennapod.transcription.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TranscriptAssemblerTest {
    @Test
    public void groupsTimedTokensAndAssignsSpeakerByOverlap() {
        List<GeneratedTranscript.Segment> segments = TranscriptAssembler.assemble(
                10_000, 5_000, "Hello world. Next.",
                new String[]{" Hello", " world", ".", " Next", "."},
                new float[]{0.0f, 0.5f, 1.0f, 2.0f, 2.5f},
                List.of(new SpeakerInterval(0, 1_500, 0), new SpeakerInterval(1_500, 5_000, 1)));

        assertEquals(2, segments.size());
        assertEquals(10_000, segments.get(0).getStartTimeMs());
        assertEquals(12_000, segments.get(0).getEndTimeMs());
        assertEquals("Hello world.", segments.get(0).getBody());
        assertEquals("Speaker 1", segments.get(0).getSpeaker());
        assertEquals("Next.", segments.get(1).getBody());
        assertEquals("Speaker 2", segments.get(1).getSpeaker());
    }

    @Test
    public void missingTokenTimesProducesSeekableChunkSegment() {
        List<GeneratedTranscript.Segment> segments = TranscriptAssembler.assemble(
                30_000, 20_000, "A complete chunk", new String[0], new float[0], List.of());

        assertEquals(1, segments.size());
        assertEquals(30_000, segments.get(0).getStartTimeMs());
        assertEquals(50_000, segments.get(0).getEndTimeMs());
        assertEquals("A complete chunk", segments.get(0).getBody());
        assertEquals("", segments.get(0).getSpeaker());
    }
}
