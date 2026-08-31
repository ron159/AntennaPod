package de.danoeh.antennapod.transcription.service;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class TranscriptionPreferencesTest {
    private TranscriptionPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_transcription", Context.MODE_PRIVATE).edit().clear().commit();
        preferences = new TranscriptionPreferences(context);
    }

    @Test
    public void modelDownloadProgressPersistsUntilCleared() {
        assertEquals(-1, preferences.getModelDownloadProgress("whisper-tiny-int8"));

        preferences.setModelDownloadProgress("whisper-tiny-int8", 37);
        assertEquals(37, preferences.getModelDownloadProgress("whisper-tiny-int8"));

        preferences.clearModelDownloadProgress("whisper-tiny-int8");
        assertEquals(-1, preferences.getModelDownloadProgress("whisper-tiny-int8"));
    }

    @Test
    public void modelDownloadProgressIsSeparatedByModel() {
        preferences.setModelDownloadProgress("whisper-tiny-int8", 12);
        preferences.setModelDownloadProgress("sense-voice-int8", 48);

        assertEquals(12, preferences.getModelDownloadProgress("whisper-tiny-int8"));
        assertEquals(48, preferences.getModelDownloadProgress("sense-voice-int8"));
    }
}
