package de.danoeh.antennapod.transcription.service;

import android.content.Context;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionServiceInterface;

import java.io.File;

final class TranscriptionFileStore {
    private TranscriptionFileStore() {
    }

    static File transcript(Context context, FeedMedia media) {
        File root = root(context, media.getId());
        return new File(root, "transcript.json");
    }

    static File aiResult(Context context, FeedMedia media, TranscriptionServiceInterface.AiAction action) {
        File root = root(context, media.getId());
        return new File(root, action == TranscriptionServiceInterface.AiAction.SUMMARY
                ? "summary.txt" : "translation.txt");
    }

    private static File root(Context context, long mediaId) {
        return new File(new File(context.getNoBackupFilesDir(), "generated-transcripts"), Long.toString(mediaId));
    }
}
