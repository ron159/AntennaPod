package de.danoeh.antennapod.playback.service.internal;

import android.media.AudioManager;
import de.danoeh.antennapod.storage.preferences.UserPreferences.AudioInterruptionBehavior;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackAudioFocusControllerTest {
    private FakeAudioFocusPlatform platform;
    private FakeCallback callback;
    private AudioInterruptionBehavior behavior;
    private PlaybackAudioFocusController controller;

    @Before
    public void setUp() {
        platform = new FakeAudioFocusPlatform();
        callback = new FakeCallback();
        behavior = AudioInterruptionBehavior.PAUSE;
        controller = new PlaybackAudioFocusController(platform, callback, () -> behavior);
    }

    @Test
    public void grantedFocusPlaysNow() {
        platform.requestResult = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

        assertEquals(PlaybackAudioFocusController.PlayRequestResult.PLAY_NOW,
                controller.requestFocusForPlayback());
        assertEquals(1, platform.requestCount);
        assertEquals(PlaybackAudioFocusController.PlayRequestResult.PLAY_NOW,
                controller.requestFocusForPlayback());
        assertEquals(1, platform.requestCount);
    }

    @Test
    public void delayedFocusWaitsAndStartsAfterGain() {
        platform.requestResult = AudioManager.AUDIOFOCUS_REQUEST_DELAYED;

        assertEquals(PlaybackAudioFocusController.PlayRequestResult.WAITING_FOR_FOCUS,
                controller.requestFocusForPlayback());
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(1, callback.delayedStartCount);
    }

    @Test
    public void deniedFocusDoesNotStartAfterGain() {
        platform.requestResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED;

        assertEquals(PlaybackAudioFocusController.PlayRequestResult.DENIED,
                controller.requestFocusForPlayback());
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(0, callback.delayedStartCount);
        assertEquals(0, callback.resumeCount);
    }

    @Test
    public void canDuckPausesAndResumesInPauseMode() {
        requestGrantedFocus();

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(1, callback.transientPauseCount);
        assertEquals(1, callback.resumeCount);
    }

    @Test
    public void canDuckLowersAndRestoresVolumeInDuckMode() {
        behavior = AudioInterruptionBehavior.DUCK;
        requestGrantedFocus();

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        assertEquals(0.25f, callback.volumeMultiplier, 0.0f);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(1.0f, callback.volumeMultiplier, 0.0f);
        assertEquals(0, callback.transientPauseCount);
    }

    @Test
    public void canDuckDoesNotPauseOrLowerVolumeInKeepPlayingMode() {
        behavior = AudioInterruptionBehavior.KEEP_PLAYING;
        requestGrantedFocus();

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);

        assertEquals(1.0f, callback.volumeMultiplier, 0.0f);
        assertEquals(0, callback.transientPauseCount);
    }

    @Test
    public void transientLossPausesAllModes() {
        for (AudioInterruptionBehavior interruptionBehavior : AudioInterruptionBehavior.values()) {
            setUp();
            behavior = interruptionBehavior;
            requestGrantedFocus();

            controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

            assertEquals(1, callback.transientPauseCount);
        }
    }

    @Test
    public void transientLossDoesNotResumePlaybackThatWasNotPlaying() {
        requestGrantedFocus();
        callback.playWhenReady = false;

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(0, callback.transientPauseCount);
        assertEquals(0, callback.resumeCount);
    }

    @Test
    public void permanentLossDoesNotResumeAfterGain() {
        requestGrantedFocus();

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(1, callback.permanentPauseCount);
        assertEquals(1, platform.abandonCount);
        assertEquals(0, callback.resumeCount);
    }

    @Test
    public void userPausePreventsResumeAfterGain() {
        requestGrantedFocus();
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        controller.onUserPause();
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(0, callback.resumeCount);
    }

    @Test
    public void userPauseCancelsDelayedFocusRequest() {
        platform.requestResult = AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
        controller.requestFocusForPlayback();

        controller.onUserPause();
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(1, platform.abandonCount);
        assertEquals(0, callback.delayedStartCount);
    }

    @Test
    public void transientLossAfterDuckRestoresVolumeBeforePausing() {
        behavior = AudioInterruptionBehavior.DUCK;
        requestGrantedFocus();
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        assertEquals(1.0f, callback.volumeMultiplier, 0.0f);
        assertEquals(1, callback.transientPauseCount);
    }

    @Test
    public void permanentLossAfterTransientLossClearsResume() {
        requestGrantedFocus();
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(0, callback.resumeCount);
    }

    @Test
    public void repeatedDuckAndGainRemainIdempotent() {
        behavior = AudioInterruptionBehavior.DUCK;
        requestGrantedFocus();

        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        assertEquals(0.25f, callback.volumeMultiplier, 0.0f);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(1.0f, callback.volumeMultiplier, 0.0f);
        assertEquals(0, callback.transientPauseCount);
    }

    @Test
    public void remotePlaybackDoesNotRequestLocalFocus() {
        callback.localPlayback = false;

        assertEquals(PlaybackAudioFocusController.PlayRequestResult.PLAY_NOW,
                controller.requestFocusForPlayback());
        assertEquals(0, platform.requestCount);
    }

    @Test
    public void releaseAbandonsFocusAndIgnoresCallbacks() {
        requestGrantedFocus();

        controller.release();
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        assertEquals(1, platform.abandonCount);
        assertEquals(0, callback.transientPauseCount);
        assertEquals(0, callback.resumeCount);
    }

    private void requestGrantedFocus() {
        platform.requestResult = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        assertEquals(PlaybackAudioFocusController.PlayRequestResult.PLAY_NOW,
                controller.requestFocusForPlayback());
    }

    private static class FakeAudioFocusPlatform implements PlaybackAudioFocusController.AudioFocusPlatform {
        private int requestResult;
        private int requestCount;
        private int abandonCount;

        @Override
        public int requestFocus() {
            requestCount++;
            return requestResult;
        }

        @Override
        public void abandonFocus() {
            abandonCount++;
        }
    }

    private static class FakeCallback implements PlaybackAudioFocusController.Callback {
        private boolean localPlayback = true;
        private boolean playWhenReady = true;
        private int transientPauseCount;
        private int delayedStartCount;
        private int resumeCount;
        private int permanentPauseCount;
        private float volumeMultiplier = 1.0f;

        @Override
        public boolean isLocalPlayback() {
            return localPlayback;
        }

        @Override
        public boolean getPlayWhenReady() {
            return playWhenReady;
        }

        @Override
        public boolean isPlaying() {
            return playWhenReady;
        }

        @Override
        public void pauseForTransientAudioFocusLoss() {
            transientPauseCount++;
            playWhenReady = false;
        }

        @Override
        public void startAfterDelayedAudioFocusGain() {
            delayedStartCount++;
            playWhenReady = true;
        }

        @Override
        public void resumeAfterTransientAudioFocusLoss() {
            resumeCount++;
            playWhenReady = true;
        }

        @Override
        public void pauseForPermanentAudioFocusLoss() {
            permanentPauseCount++;
            playWhenReady = false;
        }

        @Override
        public void setFocusVolumeMultiplier(float multiplier) {
            volumeMultiplier = multiplier;
        }
    }
}
