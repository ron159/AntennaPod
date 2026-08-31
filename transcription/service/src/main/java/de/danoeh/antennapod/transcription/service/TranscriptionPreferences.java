package de.danoeh.antennapod.transcription.service;

import android.content.Context;
import android.content.SharedPreferences;

final class TranscriptionPreferences {
    private static final String NAME = "local_transcription";
    private static final String SELECTED_MODEL = "selected_model";
    private static final String AUTOMATIC = "automatic";
    private static final String SPEAKER_LABELS = "speaker_labels";
    private static final String AI_BASE_URL = "ai_base_url";
    private static final String AI_MODEL = "ai_model";
    private static final String AI_TRANSLATION_LANGUAGE = "ai_translation_language";
    private static final String MODEL_DOWNLOAD_PROGRESS = "model_download_progress:";

    private final SharedPreferences preferences;

    TranscriptionPreferences(Context context) {
        preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    String getSelectedModel() {
        return preferences.getString(SELECTED_MODEL, "sense-voice-int8");
    }

    void setSelectedModel(String value) {
        preferences.edit().putString(SELECTED_MODEL, value).apply();
    }

    boolean isAutomatic() {
        return preferences.getBoolean(AUTOMATIC, false);
    }

    void setAutomatic(boolean value) {
        preferences.edit().putBoolean(AUTOMATIC, value).apply();
    }

    boolean isSpeakerLabels() {
        return preferences.getBoolean(SPEAKER_LABELS, false);
    }

    void setSpeakerLabels(boolean value) {
        preferences.edit().putBoolean(SPEAKER_LABELS, value).apply();
    }

    int getModelDownloadProgress(String modelId) {
        return preferences.getInt(MODEL_DOWNLOAD_PROGRESS + modelId, -1);
    }

    void setModelDownloadProgress(String modelId, int progress) {
        preferences.edit().putInt(MODEL_DOWNLOAD_PROGRESS + modelId, progress).apply();
    }

    void clearModelDownloadProgress(String modelId) {
        preferences.edit().remove(MODEL_DOWNLOAD_PROGRESS + modelId).apply();
    }

    String getAiBaseUrl() {
        return preferences.getString(AI_BASE_URL, "https://api.openai.com/v1");
    }

    String getAiModel() {
        return preferences.getString(AI_MODEL, "gpt-4.1-mini");
    }

    String getAiTranslationLanguage() {
        return preferences.getString(AI_TRANSLATION_LANGUAGE, "Simplified Chinese");
    }

    void setAi(String baseUrl, String model, String translationLanguage) {
        preferences.edit().putString(AI_BASE_URL, baseUrl).putString(AI_MODEL, model)
                .putString(AI_TRANSLATION_LANGUAGE, translationLanguage).apply();
    }
}
