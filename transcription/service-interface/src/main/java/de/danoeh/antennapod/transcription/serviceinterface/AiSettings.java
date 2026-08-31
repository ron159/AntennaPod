package de.danoeh.antennapod.transcription.serviceinterface;

public class AiSettings {
    private final String baseUrl;
    private final String model;
    private final String translationLanguage;
    private final boolean apiKeyStored;

    public AiSettings(String baseUrl, String model, String translationLanguage, boolean apiKeyStored) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.translationLanguage = translationLanguage;
        this.apiKeyStored = apiKeyStored;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getTranslationLanguage() {
        return translationLanguage;
    }

    public boolean isApiKeyStored() {
        return apiKeyStored;
    }
}
