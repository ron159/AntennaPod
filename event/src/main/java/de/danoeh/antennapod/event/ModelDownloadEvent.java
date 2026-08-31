package de.danoeh.antennapod.event;

public class ModelDownloadEvent {
    public enum State {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELED
    }

    private final String modelId;
    private final int progress;
    private final State state;

    public ModelDownloadEvent(String modelId, int progress, State state) {
        this.modelId = modelId;
        this.progress = progress;
        this.state = state;
    }

    public String getModelId() {
        return modelId;
    }

    public int getProgress() {
        return progress;
    }

    public State getState() {
        return state;
    }
}
