package de.danoeh.antennapod.transcription.service;

import java.io.IOException;
import java.util.Arrays;

final class PcmWindowCollector {
    interface Consumer {
        void accept(float[] samples, long offsetMs) throws IOException;
    }

    private final int targetRate;
    private final float[] window;
    private final Consumer consumer;
    private int sourceRate;
    private long sourceFramesSeen;
    private double nextSourceFrame;
    private long targetFramesEmitted;
    private int windowSize;

    PcmWindowCollector(int targetRate, int windowSeconds, Consumer consumer) {
        this.targetRate = targetRate;
        this.window = new float[targetRate * windowSeconds];
        this.consumer = consumer;
    }

    void accept(float[] mono, int rate) throws IOException {
        if (sourceRate != rate) {
            sourceRate = rate;
            sourceFramesSeen = 0;
            nextSourceFrame = 0;
        }
        long blockStart = sourceFramesSeen;
        long blockEnd = blockStart + mono.length;
        double step = rate / (double) targetRate;
        while (nextSourceFrame < blockEnd) {
            int index = (int) Math.max(0, Math.min(mono.length - 1, nextSourceFrame - blockStart));
            window[windowSize++] = mono[index];
            nextSourceFrame += step;
            if (windowSize == window.length) {
                emit(window);
                windowSize = 0;
            }
        }
        sourceFramesSeen = blockEnd;
    }

    void finish() throws IOException {
        if (windowSize > 0) {
            emit(Arrays.copyOf(window, windowSize));
            windowSize = 0;
        }
    }

    private void emit(float[] samples) throws IOException {
        long offsetMs = targetFramesEmitted * 1_000 / targetRate;
        consumer.accept(samples, offsetMs);
        targetFramesEmitted += samples.length;
    }
}
