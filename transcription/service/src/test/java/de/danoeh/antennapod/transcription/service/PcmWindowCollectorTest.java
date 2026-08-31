package de.danoeh.antennapod.transcription.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PcmWindowCollectorTest {
    @Test
    public void downmixStreamIsResampledIntoBoundedWindows() throws Exception {
        List<float[]> windows = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();
        PcmWindowCollector collector = new PcmWindowCollector(2, 2, (samples, offset) -> {
            windows.add(samples.clone());
            offsets.add(offset);
        });

        collector.accept(new float[]{0, 1, 2, 3}, 4);
        collector.accept(new float[]{4, 5, 6, 7, 8, 9}, 4);
        collector.finish();

        assertEquals(2, windows.size());
        assertArrayEquals(new float[]{0, 2, 4, 6}, windows.get(0), 0.0f);
        assertArrayEquals(new float[]{8}, windows.get(1), 0.0f);
        assertEquals(List.of(0L, 2_000L), offsets);
    }
}
