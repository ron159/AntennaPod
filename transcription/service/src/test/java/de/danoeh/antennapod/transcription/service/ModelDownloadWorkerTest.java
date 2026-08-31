package de.danoeh.antennapod.transcription.service;

import android.content.pm.ServiceInfo;
import android.os.Build;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModelDownloadWorkerTest {
    @Test
    public void foregroundServiceUsesDataSyncTypeOnModernAndroid() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                ModelDownloadWorker.foregroundServiceType(Build.VERSION_CODES.VANILLA_ICE_CREAM));
    }

    @Test
    public void foregroundServiceHasNoTypeBeforeAndroidTen() {
        assertEquals(0, ModelDownloadWorker.foregroundServiceType(Build.VERSION_CODES.P));
    }

    @Test
    public void progressIsOnlyReportedWhenPercentageChanges() {
        assertEquals(false, ModelDownloadWorker.shouldReportProgress(6, 6));
        assertEquals(true, ModelDownloadWorker.shouldReportProgress(6, 7));
    }

    @Test
    public void progressPercentageIsBounded() {
        assertEquals(0, ModelDownloadWorker.progressPercent(0, 100));
        assertEquals(42, ModelDownloadWorker.progressPercent(42, 100));
        assertEquals(100, ModelDownloadWorker.progressPercent(120, 100));
    }
}
