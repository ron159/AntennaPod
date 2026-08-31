package de.danoeh.antennapod.transcription.serviceinterface;

import android.content.Context;
import de.danoeh.antennapod.model.feed.FeedMedia;

import java.io.File;
import java.io.IOException;

public abstract class TranscriptionServiceInterface {
    public enum AiAction {
        SUMMARY,
        TRANSLATION
    }

    private static TranscriptionServiceInterface impl;

    public static TranscriptionServiceInterface get() {
        return impl;
    }

    public static void setImpl(TranscriptionServiceInterface impl) {
        TranscriptionServiceInterface.impl = impl;
    }

    public abstract boolean isModelInstalled(Context context, String modelId);

    public abstract void downloadModel(Context context, String modelId);

    public abstract int getModelDownloadProgress(Context context, String modelId);

    public abstract void deleteModel(Context context, String modelId) throws IOException;

    public abstract String getSelectedModelId(Context context);

    public abstract void setSelectedModelId(Context context, String modelId);

    public abstract boolean isAutomaticTranscriptionEnabled(Context context);

    public abstract void setAutomaticTranscriptionEnabled(Context context, boolean enabled);

    public abstract boolean isSpeakerLabelsEnabled(Context context);

    public abstract void setSpeakerLabelsEnabled(Context context, boolean enabled);

    public abstract void transcribe(Context context, long mediaId, boolean replaceExisting);

    public abstract void transcribeAfterDownload(Context context, long mediaId);

    public abstract void cancelTranscription(Context context, long mediaId);

    public abstract File getGeneratedTranscriptFile(Context context, FeedMedia media);

    public abstract boolean hasGeneratedTranscript(Context context, FeedMedia media);

    public abstract AiSettings getAiSettings(Context context);

    public abstract void saveAiSettings(Context context, String baseUrl, String model,
                                        String translationLanguage, String apiKey);

    public abstract void requestAi(Context context, long mediaId, AiAction action);

    public abstract File getAiResultFile(Context context, FeedMedia media, AiAction action);
}
