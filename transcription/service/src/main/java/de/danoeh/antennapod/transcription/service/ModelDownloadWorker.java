package de.danoeh.antennapod.transcription.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.storage.StorageManager;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.event.ModelDownloadEvent;
import de.danoeh.antennapod.event.TranscriptionEvent;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModel;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModels;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class ModelDownloadWorker extends Worker {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long SPACE_MARGIN = 50L * 1024 * 1024;

    public ModelDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String modelId = getInputData().getString(TranscriptionServiceImpl.MODEL_ID);
        TranscriptionModel model = TranscriptionModels.findById(modelId);
        if (model == null) {
            return Result.failure();
        }
        try {
            TranscriptionPreferences preferences = new TranscriptionPreferences(getApplicationContext());
            int initialProgress = Math.max(0, preferences.getModelDownloadProgress(model.getId()));
            preferences.setModelDownloadProgress(model.getId(), initialProgress);
            EventBus.getDefault().post(new ModelDownloadEvent(
                    model.getId(), initialProgress, ModelDownloadEvent.State.RUNNING));
            setForegroundAsync(foreground(model, initialProgress)).get();
            download(model);
            preferences.clearModelDownloadProgress(model.getId());
            EventBus.getDefault().post(new ModelDownloadEvent(
                    model.getId(), 100, ModelDownloadEvent.State.SUCCEEDED));
            EventBus.getDefault().post(new TranscriptionEvent(
                    TranscriptionEvent.Type.MODEL_CHANGED, 0, model.getId()));
            return Result.success();
        } catch (IOException | ExecutionException e) {
            if (getRunAttemptCount() < 2 && !isStopped()) {
                EventBus.getDefault().post(new ModelDownloadEvent(model.getId(),
                        getStoredProgress(model), ModelDownloadEvent.State.QUEUED));
                return Result.retry();
            }
            return fail(model, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return isStopped() ? Result.failure() : fail(model, e.getMessage());
        }
    }

    private void download(TranscriptionModel model) throws IOException {
        ModelRepository repository = ModelRepository.from(getApplicationContext());
        long downloadedBefore = 0;
        for (TranscriptionModel.File modelFile : model.getFiles()) {
            File target = repository.getFile(model, modelFile);
            if (repository.isValid(target, modelFile)) {
                downloadedBefore += modelFile.getSizeBytes();
                continue;
            }
            ensureSpace(model.getDownloadSizeBytes() - downloadedBefore);
            downloadFile(model, modelFile, target, downloadedBefore, repository);
            downloadedBefore += modelFile.getSizeBytes();
        }
    }

    private void downloadFile(TranscriptionModel model, TranscriptionModel.File modelFile, File target,
                              long downloadedBefore, ModelRepository repository) throws IOException {
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Could not create the model directory");
        }
        File partial = new File(target.getPath() + ".part");
        if (partial.length() > modelFile.getSizeBytes() && !partial.delete()) {
            throw new IOException("Could not reset an invalid partial download");
        }
        long offset = partial.length();
        Request.Builder request = new Request.Builder().url(modelFile.getUrl());
        if (offset > 0) {
            request.header("Range", "bytes=" + offset + "-");
        }
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Model download failed with HTTP " + response.code());
            }
            boolean append = offset > 0 && response.code() == 206;
            if (!append) {
                offset = 0;
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Model download returned no data");
            }
            writeResponse(model, modelFile, partial, body.byteStream(), append, offset, downloadedBefore);
        }
        if (!repository.isValid(partial, modelFile)) {
            throw new IOException("Downloaded model file failed SHA-256 verification");
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace an invalid model file");
        }
        if (!partial.renameTo(target)) {
            throw new IOException("Could not finish the model download");
        }
    }

    private void writeResponse(TranscriptionModel model, TranscriptionModel.File modelFile, File partial,
                               InputStream input, boolean append, long offset, long downloadedBefore)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long fileBytes = offset;
        int lastReported = getStoredProgress(model);
        int resumedProgress = progressPercent(downloadedBefore + fileBytes, model.getDownloadSizeBytes());
        if (shouldReportProgress(lastReported, resumedProgress)) {
            reportProgress(model, resumedProgress);
            lastReported = resumedProgress;
        }
        try (FileOutputStream output = new FileOutputStream(partial, append)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (isStopped()) {
                    throw new IOException("Model download was cancelled");
                }
                output.write(buffer, 0, count);
                fileBytes += count;
                if (fileBytes > modelFile.getSizeBytes()) {
                    throw new IOException("Model download exceeded the expected size");
                }
                int percent = progressPercent(downloadedBefore + fileBytes, model.getDownloadSizeBytes());
                if (shouldReportProgress(lastReported, percent)) {
                    reportProgress(model, percent);
                    lastReported = percent;
                }
            }
        }
    }

    private int getStoredProgress(TranscriptionModel model) {
        return new TranscriptionPreferences(getApplicationContext()).getModelDownloadProgress(model.getId());
    }

    private void reportProgress(TranscriptionModel model, int progress) {
        new TranscriptionPreferences(getApplicationContext()).setModelDownloadProgress(model.getId(), progress);
        setProgressAsync(new Data.Builder().putInt("progress", progress).build());
        setForegroundAsync(foreground(model, progress));
        EventBus.getDefault().post(new ModelDownloadEvent(
                model.getId(), progress, ModelDownloadEvent.State.RUNNING));
    }

    private Result fail(TranscriptionModel model, String error) {
        new TranscriptionPreferences(getApplicationContext()).clearModelDownloadProgress(model.getId());
        EventBus.getDefault().post(new ModelDownloadEvent(
                model.getId(), -1, ModelDownloadEvent.State.FAILED));
        EventBus.getDefault().post(new TranscriptionEvent(
                TranscriptionEvent.Type.FAILED, 0, error));
        String details = error == null ? "Model download failed" : error;
        return Result.failure(new Data.Builder().putString("error", details).build());
    }

    static boolean shouldReportProgress(int previous, int current) {
        return previous != current;
    }

    static int progressPercent(long downloaded, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) (Math.min(downloaded, total) * 100 / total);
    }

    private void ensureSpace(long remaining) throws IOException {
        Context context = getApplicationContext();
        long available;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            StorageManager storageManager = context.getSystemService(StorageManager.class);
            available = storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT);
        } else {
            available = context.getNoBackupFilesDir().getUsableSpace();
        }
        if (available < remaining + SPACE_MARGIN) {
            throw new IOException("Not enough free space for this model");
        }
    }

    private ForegroundInfo foreground(TranscriptionModel model, int progress) {
        Notification notification = new NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setSmallIcon(de.danoeh.antennapod.ui.notifications.R.drawable.ic_notification_sync)
                .setContentTitle(getApplicationContext().getString(
                        de.danoeh.antennapod.ui.i18n.R.string.transcription_downloading_model))
                .setContentText(model.getDisplayName())
                .setProgress(100, progress, progress == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        int serviceType = foregroundServiceType(Build.VERSION.SDK_INT);
        if (serviceType != 0) {
            return new ForegroundInfo(R.id.notification_model_download, notification, serviceType);
        }
        return new ForegroundInfo(R.id.notification_model_download, notification);
    }

    @SuppressLint("InlinedApi")
    static int foregroundServiceType(int sdkVersion) {
        return sdkVersion >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
    }
}
