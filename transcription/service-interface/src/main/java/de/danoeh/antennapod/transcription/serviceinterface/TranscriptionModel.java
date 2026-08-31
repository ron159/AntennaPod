package de.danoeh.antennapod.transcription.serviceinterface;

import java.util.List;

public class TranscriptionModel {
    public enum Engine {
        WHISPER,
        SENSE_VOICE,
        QWEN3_ASR,
        SPEAKER_DIARIZATION
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final Engine engine;
    private final List<String> languages;
    private final List<File> files;
    private final int recommendedRamMb;
    private final boolean recommended;
    private final boolean tokenTimestamps;

    public TranscriptionModel(String id, String displayName, String description, Engine engine,
                              List<String> languages, List<File> files, int recommendedRamMb,
                              boolean recommended, boolean tokenTimestamps) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.engine = engine;
        this.languages = List.copyOf(languages);
        this.files = List.copyOf(files);
        this.recommendedRamMb = recommendedRamMb;
        this.recommended = recommended;
        this.tokenTimestamps = tokenTimestamps;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Engine getEngine() {
        return engine;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public List<File> getFiles() {
        return files;
    }

    public long getDownloadSizeBytes() {
        long total = 0;
        for (File file : files) {
            total += file.sizeBytes;
        }
        return total;
    }

    public int getRecommendedRamMb() {
        return recommendedRamMb;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public boolean hasTokenTimestamps() {
        return tokenTimestamps;
    }

    public static class File {
        private final String relativePath;
        private final String url;
        private final long sizeBytes;
        private final String sha256;

        public File(String relativePath, String url, long sizeBytes, String sha256) {
            if (relativePath.startsWith("/") || relativePath.contains("..")) {
                throw new IllegalArgumentException("Unsafe model file path");
            }
            this.relativePath = relativePath;
            this.url = url;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getUrl() {
            return url;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getSha256() {
            return sha256;
        }
    }
}
