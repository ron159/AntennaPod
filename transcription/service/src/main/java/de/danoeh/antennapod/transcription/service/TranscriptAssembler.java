package de.danoeh.antennapod.transcription.service;

import java.util.ArrayList;
import java.util.List;

public final class TranscriptAssembler {
    private static final long MAX_SEGMENT_MS = 8_000;

    private TranscriptAssembler() {
    }

    public static List<GeneratedTranscript.Segment> assemble(long offsetMs, long durationMs, String text,
                                                              String[] tokens, float[] timestamps,
                                                              List<SpeakerInterval> speakers) {
        if (tokens.length == 0 || tokens.length != timestamps.length) {
            String body = text.trim();
            if (body.isEmpty()) {
                return List.of();
            }
            return List.of(new GeneratedTranscript.Segment(offsetMs, offsetMs + durationMs, body,
                    findSpeaker(0, durationMs, speakers)));
        }
        List<GeneratedTranscript.Segment> result = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        long segmentStart = timeMs(timestamps[0], durationMs);
        for (int i = 0; i < tokens.length; i++) {
            String token = normalize(tokens[i]);
            if (!token.isEmpty()) {
                body.append(token);
            }
            long nextTime = i + 1 < timestamps.length ? timeMs(timestamps[i + 1], durationMs) : durationMs;
            boolean sentenceEnd = token.matches(".*[.!?。！？]$");
            boolean spanEnd = nextTime - segmentStart >= MAX_SEGMENT_MS;
            if ((sentenceEnd || spanEnd || i == tokens.length - 1) && !body.toString().trim().isEmpty()) {
                String speaker = findSpeaker(segmentStart, nextTime, speakers);
                result.add(new GeneratedTranscript.Segment(offsetMs + segmentStart, offsetMs + nextTime,
                        body.toString().trim(), speaker));
                body.setLength(0);
                segmentStart = nextTime;
            }
        }
        return result;
    }

    private static long timeMs(float seconds, long durationMs) {
        return Math.max(0, Math.min(durationMs, Math.round(seconds * 1_000)));
    }

    private static String normalize(String token) {
        if (token.startsWith("<|") && token.endsWith("|>")) {
            return "";
        }
        return token.replace('▁', ' ');
    }

    private static String findSpeaker(long startMs, long endMs, List<SpeakerInterval> speakers) {
        long bestOverlap = 0;
        int bestSpeaker = -1;
        for (SpeakerInterval interval : speakers) {
            long overlap = Math.max(0, Math.min(endMs, interval.getEndTimeMs())
                    - Math.max(startMs, interval.getStartTimeMs()));
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestSpeaker = interval.getSpeaker();
            }
        }
        return bestSpeaker < 0 ? "" : "Speaker " + (bestSpeaker + 1);
    }
}
