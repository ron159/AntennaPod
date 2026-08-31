package de.danoeh.antennapod.transcription.service;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModel;

public class ModelRepository {
    private final File root;

    public ModelRepository(File root) {
        this.root = root;
    }

    public static ModelRepository from(Context context) {
        return new ModelRepository(new File(context.getNoBackupFilesDir(), "transcription-models"));
    }

    public File getModelDirectory(String modelId) {
        if (!modelId.matches("[a-z0-9][a-z0-9.-]*")) {
            throw new IllegalArgumentException("Invalid model id");
        }
        return new File(root, modelId);
    }

    public File getFile(TranscriptionModel model, TranscriptionModel.File modelFile) {
        File modelDirectory = getModelDirectory(model.getId());
        File target = new File(modelDirectory, modelFile.getRelativePath());
        try {
            String parent = modelDirectory.getCanonicalPath() + File.separator;
            if (!target.getCanonicalPath().startsWith(parent)) {
                throw new IllegalArgumentException("Unsafe model file path");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid model file path", e);
        }
        return target;
    }

    public boolean isInstalled(TranscriptionModel model) {
        for (TranscriptionModel.File modelFile : model.getFiles()) {
            if (!isValid(getFile(model, modelFile), modelFile)) {
                return false;
            }
        }
        return true;
    }

    public boolean isValid(File file, TranscriptionModel.File modelFile) {
        if (!file.isFile() || file.length() != modelFile.getSizeBytes()) {
            return false;
        }
        try {
            return digest(file).equals(modelFile.getSha256());
        } catch (IOException e) {
            return false;
        }
    }

    public void delete(TranscriptionModel model) throws IOException {
        deleteRecursively(getModelDirectory(model.getId()));
    }

    private static String digest(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Unable to list model directory");
            }
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("Unable to delete model file");
        }
    }
}
