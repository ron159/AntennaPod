package de.danoeh.antennapod.transcription.service;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.TranscriptionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModel;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModels;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class LocalTranscriptionWorker extends Worker {
    public LocalTranscriptionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long mediaId = getInputData().getLong(TranscriptionServiceImpl.MEDIA_ID, 0);
        String modelId = getInputData().getString(TranscriptionServiceImpl.MODEL_ID);
        FeedMedia media = DBReader.getFeedMedia(mediaId);
        TranscriptionModel model = TranscriptionModels.findById(modelId);
        if (media == null || model == null || media.getLocalFileUrl() == null) {
            return Result.failure();
        }
        File destination = TranscriptionFileStore.transcript(getApplicationContext(), media);
        if (destination.isFile() && !getInputData().getBoolean("replace_existing", false)) {
            return Result.success();
        }
        try {
            setForegroundAsync(foreground(media, 0)).get();
            GeneratedTranscript transcript = transcribe(media, model);
            if (transcript.getSegments().isEmpty()) {
                throw new IOException("The local model did not produce any transcript text");
            }
            writeAtomically(destination, GeneratedTranscriptJson.write(transcript));
            EventBus.getDefault().post(new TranscriptionEvent(
                    TranscriptionEvent.Type.TRANSCRIPT_READY, mediaId, destination.getAbsolutePath()));
            EventBus.getDefault().post(new MessageEvent(getApplicationContext().getString(
                    de.danoeh.antennapod.ui.i18n.R.string.transcription_completed)));
            return Result.success();
        } catch (IOException | ExecutionException e) {
            if (getRunAttemptCount() < 1 && !isStopped()) {
                return Result.retry();
            }
            EventBus.getDefault().post(new TranscriptionEvent(
                    TranscriptionEvent.Type.FAILED, mediaId, e.getMessage()));
            EventBus.getDefault().post(new MessageEvent(getApplicationContext().getString(
                    de.danoeh.antennapod.ui.i18n.R.string.transcription_failed)));
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure();
        }
    }

    private GeneratedTranscript transcribe(FeedMedia media, TranscriptionModel model) throws IOException {
        ModelRepository repository = ModelRepository.from(getApplicationContext());
        if (!repository.isInstalled(model)) {
            throw new IOException("The selected local model is not installed");
        }
        boolean speakerLabels = new TranscriptionPreferences(getApplicationContext()).isSpeakerLabels();
        List<GeneratedTranscript.Segment> segments = new ArrayList<>();
        try (SherpaEngine engine = new SherpaEngine(model, repository, speakerLabels)) {
            MediaAudioDecoder.decode(getApplicationContext(), media.getLocalFileUrl(), (samples, offsetMs) -> {
                segments.addAll(engine.transcribeWindow(samples, offsetMs));
                int progress = media.getDuration() <= 0 ? 0 : (int) Math.min(100,
                        (offsetMs + samples.length * 1_000L / 16_000) * 100 / media.getDuration());
                setProgressAsync(new Data.Builder().putInt("progress", progress).build());
                setForegroundAsync(foreground(media, progress));
            }, this::isStopped);
        }
        return new GeneratedTranscript(model.getId(), segments);
    }

    private ForegroundInfo foreground(FeedMedia media, int progress) {
        Notification notification = new NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setSmallIcon(de.danoeh.antennapod.ui.notifications.R.drawable.ic_notification_sync)
                .setContentTitle(getApplicationContext().getString(
                        de.danoeh.antennapod.ui.i18n.R.string.transcription_in_progress))
                .setContentText(media.getEpisodeTitle())
                .setProgress(100, progress, progress == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        if (Build.VERSION.SDK_INT >= 35) {
            return new ForegroundInfo(R.id.notification_transcription, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        }
        return new ForegroundInfo(R.id.notification_transcription, notification);
    }

    static void writeAtomically(File destination, String value) throws IOException {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Could not create generated transcript directory");
        }
        File temporary = new File(destination.getPath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Could not replace generated transcript");
        }
        if (!temporary.renameTo(destination)) {
            throw new IOException("Could not finish generated transcript");
        }
    }
}
