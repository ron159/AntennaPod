package de.danoeh.antennapod.transcription.service;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class GeneratedTranscriptJson {
    private GeneratedTranscriptJson() {
    }

    public static String write(GeneratedTranscript transcript) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", "1.0.0");
            root.put("generatedBy", new JSONObject().put("engine", "sherpa-onnx")
                    .put("model", transcript.getModelId()));
            JSONArray segments = new JSONArray();
            for (GeneratedTranscript.Segment segment : transcript.getSegments()) {
                JSONObject value = new JSONObject();
                value.put("startTime", segment.getStartTimeMs() / 1_000.0);
                value.put("endTime", segment.getEndTimeMs() / 1_000.0);
                value.put("body", segment.getBody());
                if (!segment.getSpeaker().isEmpty()) {
                    value.put("speaker", segment.getSpeaker());
                }
                segments.put(value);
            }
            root.put("segments", segments);
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
