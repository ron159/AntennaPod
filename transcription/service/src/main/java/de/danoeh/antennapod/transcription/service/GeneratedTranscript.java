package de.danoeh.antennapod.transcription.service;

import java.util.List;

public class GeneratedTranscript {
    private final String modelId;
    private final List<Segment> segments;

    public GeneratedTranscript(String modelId, List<Segment> segments) {
        this.modelId = modelId;
        this.segments = List.copyOf(segments);
    }

    public String getModelId() {
        return modelId;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public static class Segment {
        private final long startTimeMs;
        private final long endTimeMs;
        private final String body;
        private final String speaker;

        public Segment(long startTimeMs, long endTimeMs, String body, String speaker) {
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.body = body;
            this.speaker = speaker;
        }

        public long getStartTimeMs() {
            return startTimeMs;
        }

        public long getEndTimeMs() {
            return endTimeMs;
        }

        public String getBody() {
            return body;
        }

        public String getSpeaker() {
            return speaker;
        }
    }
}
