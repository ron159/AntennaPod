package de.danoeh.antennapod.transcription.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModelRepositoryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installedRequiresMatchingSizeAndDigest() throws Exception {
        File root = temporaryFolder.newFolder("models");
        ModelRepository repository = new ModelRepository(root);
        byte[] data = "model-data".getBytes(StandardCharsets.UTF_8);
        TranscriptionModel model = model(data.length,
                "78af4dc485eb32a809e795bfca78a4f5313401b58d9bc81cc4d1b89445b97333");

        assertFalse(repository.isInstalled(model));
        File target = repository.getFile(model, model.getFiles().get(0));
        assertTrue(target.getParentFile().mkdirs());
        Files.write(target.toPath(), data);
        assertTrue(repository.isInstalled(model));

        Files.write(target.toPath(), "bad".getBytes(StandardCharsets.UTF_8));
        assertFalse(repository.isInstalled(model));
    }

    @Test(expected = IllegalArgumentException.class)
    public void modelIdCannotEscapeRoot() {
        ModelRepository repository = new ModelRepository(temporaryFolder.getRoot());
        repository.getModelDirectory("../outside");
    }

    private static TranscriptionModel model(long size, String sha256) {
        return new TranscriptionModel("test", "Test", "Test", TranscriptionModel.Engine.WHISPER,
                List.of("en"), List.of(new TranscriptionModel.File("nested/model.bin",
                "https://huggingface.co/test/resolve/revision/model.bin", size, sha256)),
                100, false, true);
    }
}
