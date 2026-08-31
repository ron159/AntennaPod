package de.danoeh.antennapod.transcription.service;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import de.danoeh.antennapod.event.ModelDownloadEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.transcription.serviceinterface.AiSettings;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModel;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModels;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionServiceInterface;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;

public class TranscriptionServiceImpl extends TranscriptionServiceInterface {
    static final String MODEL_ID = "model_id";
    static final String MEDIA_ID = "media_id";
    static final String AI_ACTION = "ai_action";
    static final String MODEL_DOWNLOAD_TAG = "transcription-model-download";
    static final String TRANSCRIPTION_TAG = "local-transcription";

    @Override
    public boolean isModelInstalled(Context context, String modelId) {
        TranscriptionModel model = requireModel(modelId);
        return ModelRepository.from(context).isInstalled(model);
    }

    @Override
    public void downloadModel(Context context, String modelId) {
        requireModel(modelId);
        TranscriptionPreferences preferences = new TranscriptionPreferences(context);
        if (preferences.getModelDownloadProgress(modelId) < 0) {
            preferences.setModelDownloadProgress(modelId, 0);
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ModelDownloadWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(new Data.Builder().putString(MODEL_ID, modelId).build())
                .addTag(MODEL_DOWNLOAD_TAG)
                .addTag(modelWorkName(modelId))
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(modelWorkName(modelId), ExistingWorkPolicy.KEEP, request);
        EventBus.getDefault().post(new ModelDownloadEvent(modelId,
                preferences.getModelDownloadProgress(modelId), ModelDownloadEvent.State.QUEUED));
    }

    @Override
    public int getModelDownloadProgress(Context context, String modelId) {
        requireModel(modelId);
        return new TranscriptionPreferences(context).getModelDownloadProgress(modelId);
    }

    @Override
    public void deleteModel(Context context, String modelId) throws IOException {
        TranscriptionModel model = requireModel(modelId);
        WorkManager.getInstance(context).cancelUniqueWork(modelWorkName(modelId));
        ModelRepository.from(context).delete(model);
        new TranscriptionPreferences(context).clearModelDownloadProgress(modelId);
        EventBus.getDefault().post(new ModelDownloadEvent(modelId, -1, ModelDownloadEvent.State.CANCELED));
    }

    @Override
    public String getSelectedModelId(Context context) {
        return new TranscriptionPreferences(context).getSelectedModel();
    }

    @Override
    public void setSelectedModelId(Context context, String modelId) {
        TranscriptionModel model = requireModel(modelId);
        if (model.getEngine() == TranscriptionModel.Engine.SPEAKER_DIARIZATION) {
            throw new IllegalArgumentException("Speaker labels are not a transcription model");
        }
        new TranscriptionPreferences(context).setSelectedModel(modelId);
    }

    @Override
    public boolean isAutomaticTranscriptionEnabled(Context context) {
        return new TranscriptionPreferences(context).isAutomatic();
    }

    @Override
    public void setAutomaticTranscriptionEnabled(Context context, boolean enabled) {
        new TranscriptionPreferences(context).setAutomatic(enabled);
    }

    @Override
    public boolean isSpeakerLabelsEnabled(Context context) {
        return new TranscriptionPreferences(context).isSpeakerLabels();
    }

    @Override
    public void setSpeakerLabelsEnabled(Context context, boolean enabled) {
        new TranscriptionPreferences(context).setSpeakerLabels(enabled);
    }

    @Override
    public void transcribe(Context context, long mediaId, boolean replaceExisting) {
        String modelId = getSelectedModelId(context);
        if (!isModelInstalled(context, modelId)) {
            throw new IllegalStateException("Selected transcription model is not installed");
        }
        Data data = new Data.Builder().putLong(MEDIA_ID, mediaId).putString(MODEL_ID, modelId)
                .putBoolean("replace_existing", replaceExisting).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LocalTranscriptionWorker.class)
                .setInputData(data).addTag(TRANSCRIPTION_TAG).addTag(mediaWorkName(mediaId)).build();
        WorkManager.getInstance(context).enqueueUniqueWork(mediaWorkName(mediaId),
                replaceExisting ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP, request);
    }

    @Override
    public void transcribeAfterDownload(Context context, long mediaId) {
        if (!isAutomaticTranscriptionEnabled(context)) {
            return;
        }
        String modelId = getSelectedModelId(context);
        if (isModelInstalled(context, modelId)) {
            transcribe(context, mediaId, false);
        }
    }

    @Override
    public void cancelTranscription(Context context, long mediaId) {
        WorkManager.getInstance(context).cancelUniqueWork(mediaWorkName(mediaId));
    }

    @Override
    public File getGeneratedTranscriptFile(Context context, FeedMedia media) {
        return TranscriptionFileStore.transcript(context, media);
    }

    @Override
    public boolean hasGeneratedTranscript(Context context, FeedMedia media) {
        return getGeneratedTranscriptFile(context, media).isFile();
    }

    @Override
    public AiSettings getAiSettings(Context context) {
        TranscriptionPreferences preferences = new TranscriptionPreferences(context);
        return new AiSettings(preferences.getAiBaseUrl(), preferences.getAiModel(),
                preferences.getAiTranslationLanguage(), new AiTokenStore(context).hasValue());
    }

    @Override
    public void saveAiSettings(Context context, String baseUrl, String model,
                               String translationLanguage, String apiKey) {
        String normalized = baseUrl.strip().replaceAll("/+$", "");
        if (!normalized.startsWith("https://") || model.isBlank() || translationLanguage.isBlank()) {
            throw new IllegalArgumentException("A HTTPS base URL, model, and translation language are required");
        }
        new TranscriptionPreferences(context).setAi(normalized, model.strip(), translationLanguage.strip());
        new AiTokenStore(context).save(apiKey.strip());
    }

    @Override
    public void requestAi(Context context, long mediaId, AiAction action) {
        if (!new AiTokenStore(context).hasValue()) {
            throw new IllegalStateException("AI API key is not configured");
        }
        Data data = new Data.Builder().putLong(MEDIA_ID, mediaId).putString(AI_ACTION, action.name()).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TranscriptAiWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(data).build();
        WorkManager.getInstance(context).enqueueUniqueWork("transcript-ai:" + mediaId + ":" + action.name(),
                ExistingWorkPolicy.REPLACE, request);
    }

    @Override
    public File getAiResultFile(Context context, FeedMedia media, AiAction action) {
        return TranscriptionFileStore.aiResult(context, media, action);
    }

    private static TranscriptionModel requireModel(String modelId) {
        TranscriptionModel model = TranscriptionModels.findById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("Unknown transcription model");
        }
        return model;
    }

    private static String modelWorkName(String modelId) {
        return "transcription-model:" + modelId;
    }

    private static String mediaWorkName(long mediaId) {
        return "local-transcription:" + mediaId;
    }
}
