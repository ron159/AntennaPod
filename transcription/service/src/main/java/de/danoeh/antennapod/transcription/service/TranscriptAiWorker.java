package de.danoeh.antennapod.transcription.service;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.TranscriptionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.parser.transcript.TranscriptParser;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.transcription.serviceinterface.AiSettings;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionServiceInterface;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TranscriptAiWorker extends Worker {
    private static final int MAX_INPUT_CHARS = 70_000;

    public TranscriptAiWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long mediaId = getInputData().getLong(TranscriptionServiceImpl.MEDIA_ID, 0);
        String actionValue = getInputData().getString(TranscriptionServiceImpl.AI_ACTION);
        FeedMedia media = DBReader.getFeedMedia(mediaId);
        if (media == null || actionValue == null) {
            return Result.failure();
        }
        TranscriptionServiceInterface.AiAction action;
        try {
            action = TranscriptionServiceInterface.AiAction.valueOf(actionValue);
            Transcript transcript = loadTranscript(media);
            if (transcript == null) {
                throw new IOException("No transcript is available for AI processing");
            }
            String result = request(transcript, action);
            File destination = TranscriptionFileStore.aiResult(getApplicationContext(), media, action);
            LocalTranscriptionWorker.writeAtomically(destination, result);
            EventBus.getDefault().post(new TranscriptionEvent(
                    TranscriptionEvent.Type.AI_READY, mediaId, action.name()));
            EventBus.getDefault().post(new MessageEvent(getApplicationContext().getString(
                    de.danoeh.antennapod.ui.i18n.R.string.transcription_ai_completed)));
            return Result.success();
        } catch (IOException | JSONException | IllegalArgumentException e) {
            if (getRunAttemptCount() < 2 && !isStopped()) {
                return Result.retry();
            }
            EventBus.getDefault().post(new TranscriptionEvent(
                    TranscriptionEvent.Type.FAILED, mediaId, e.getMessage()));
            EventBus.getDefault().post(new MessageEvent(getApplicationContext().getString(
                    de.danoeh.antennapod.ui.i18n.R.string.transcription_ai_failed)));
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }
    }

    private Transcript loadTranscript(FeedMedia media) throws IOException {
        File cached = media.getTranscriptFileUrl() == null ? null : new File(media.getTranscriptFileUrl());
        if (cached != null && cached.isFile()) {
            return TranscriptParser.parse(FileUtils.readFileToString(cached, StandardCharsets.UTF_8),
                    media.getItem().getTranscriptType());
        }
        String url = media.getItem().getTranscriptUrl();
        if (url != null) {
            try (Response response = AntennapodHttpClient.getHttpClient().newCall(
                    new Request.Builder().url(url).build()).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Could not load the episode transcript");
                }
                return TranscriptParser.parse(body.string(), media.getItem().getTranscriptType());
            }
        }
        File generated = TranscriptionFileStore.transcript(getApplicationContext(), media);
        return generated.isFile()
                ? TranscriptParser.parse(FileUtils.readFileToString(generated, StandardCharsets.UTF_8),
                        "application/json") : null;
    }

    private String request(Transcript transcript, TranscriptionServiceInterface.AiAction action)
            throws IOException, JSONException {
        TranscriptionPreferences preferences = new TranscriptionPreferences(getApplicationContext());
        AiSettings settings = new AiSettings(preferences.getAiBaseUrl(), preferences.getAiModel(),
                preferences.getAiTranslationLanguage(), true);
        String key = new AiTokenStore(getApplicationContext()).load();
        if (key == null) {
            throw new IOException("AI API key is not configured");
        }
        String prompt = action == TranscriptionServiceInterface.AiAction.SUMMARY
                ? "Summarize this podcast transcript. Return a concise overview, key points, and action items."
                : "Translate this podcast transcript into " + settings.getTranslationLanguage()
                + ". Preserve timestamps and speaker labels.";
        JSONObject payload = new JSONObject()
                .put("model", settings.getModel())
                .put("temperature", 0.2)
                .put("max_tokens", 4096)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system")
                                .put("content", "You process podcast transcripts accurately."))
                        .put(new JSONObject().put("role", "user")
                                .put("content", prompt + "\n\n" + format(transcript))));
        Request request = new Request.Builder().url(chatCompletionsUrl(settings.getBaseUrl()))
                .header("Authorization", "Bearer " + key)
                .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("AI request failed with HTTP " + response.code());
            }
            JSONObject json = new JSONObject(body.string());
            String content = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").strip();
            if (content.isEmpty()) {
                throw new IOException("AI response contained no text");
            }
            return content;
        }
    }

    private static String format(Transcript transcript) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < transcript.getSegmentCount(); index++) {
            TranscriptSegment segment = transcript.getSegmentAt(index);
            long seconds = segment.getStartTime() / 1_000;
            text.append(String.format(Locale.US, "[%02d:%02d:%02d] ",
                    seconds / 3_600, seconds / 60 % 60, seconds % 60));
            if (segment.getSpeaker() != null && !segment.getSpeaker().isBlank()) {
                text.append(segment.getSpeaker()).append(": ");
            }
            text.append(segment.getWords()).append('\n');
            if (text.length() >= MAX_INPUT_CHARS) {
                return text.substring(0, MAX_INPUT_CHARS);
            }
        }
        return text.toString();
    }

    private static String chatCompletionsUrl(String baseUrl) {
        return baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";
    }
}
