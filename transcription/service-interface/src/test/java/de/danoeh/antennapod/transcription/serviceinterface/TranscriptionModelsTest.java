package de.danoeh.antennapod.transcription.serviceinterface;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TranscriptionModelsTest {
    @Test
    public void catalogOffersFourPinnedQuantizedModels() {
        List<TranscriptionModel> models = TranscriptionModels.getAll();

        assertEquals(4, models.size());
        assertEquals("whisper-tiny-int8", models.get(0).getId());
        assertEquals("whisper-base-int8", models.get(1).getId());
        assertEquals("sense-voice-int8", models.get(2).getId());
        assertEquals("qwen3-asr-0.6b-int8", models.get(3).getId());
        assertTrue(models.get(2).isRecommended());
    }

    @Test
    public void everyModelFileHasSafePathPinnedUrlSizeAndDigest() {
        Set<String> ids = new HashSet<>();
        for (TranscriptionModel model : TranscriptionModels.getAll()) {
            assertTrue(ids.add(model.getId()));
            assertFalse(model.getFiles().isEmpty());
            long expectedBytes = 0;
            for (TranscriptionModel.File file : model.getFiles()) {
                assertFalse(file.getRelativePath().startsWith("/"));
                assertFalse(file.getRelativePath().contains(".."));
                assertTrue(file.getUrl().startsWith("https://huggingface.co/"));
                assertFalse(file.getUrl().contains("/resolve/main/"));
                assertTrue(file.getSizeBytes() > 0);
                assertTrue(file.getSha256().matches("[0-9a-f]{64}"));
                expectedBytes += file.getSizeBytes();
            }
            assertEquals(expectedBytes, model.getDownloadSizeBytes());
        }
    }

    @Test
    public void idsResolveAndUnknownIdDoesNot() {
        assertNotNull(TranscriptionModels.findById("sense-voice-int8"));
        assertNotNull(TranscriptionModels.findById("speaker-diarization-int8"));
        assertEquals(null, TranscriptionModels.findById("missing"));
    }

    @Test
    public void speakerLabelsUsePinnedVerifiedModels() {
        TranscriptionModel model = TranscriptionModels.getSpeakerDiarization();

        assertEquals(TranscriptionModel.Engine.SPEAKER_DIARIZATION, model.getEngine());
        assertEquals(2, model.getFiles().size());
        for (TranscriptionModel.File file : model.getFiles()) {
            assertTrue(file.getSizeBytes() > 0);
            assertTrue(file.getSha256().matches("[0-9a-f]{64}"));
        }
    }
}
