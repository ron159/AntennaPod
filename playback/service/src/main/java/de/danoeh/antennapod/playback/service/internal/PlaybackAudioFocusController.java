package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.RequiresApi;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences.AudioInterruptionBehavior;

public final class PlaybackAudioFocusController {
    private static final float DUCK_VOLUME_MULTIPLIER = 0.25f;

    public enum PlayRequestResult {
        PLAY_NOW,
        WAITING_FOR_FOCUS,
        DENIED
    }

    public interface Callback {
        boolean isLocalPlayback();

        boolean getPlayWhenReady();

        void pauseForTransientAudioFocusLoss();

        void startAfterDelayedAudioFocusGain();

        void resumeAfterTransientAudioFocusLoss();

        void pauseForPermanentAudioFocusLoss();

        void setFocusVolumeMultiplier(float multiplier);
    }

    interface AudioFocusPlatform {
        int requestFocus();

        void abandonFocus();
    }

    interface BehaviorProvider {
        AudioInterruptionBehavior getBehavior();
    }

    private final AudioFocusPlatform audioFocusPlatform;
    private final Callback callback;
    private final BehaviorProvider behaviorProvider;
    private boolean focusRequestActive;
    private boolean hasAudioFocus;
    private boolean waitingForDelayedFocusGain;
    private boolean resumeOnFocusGain;
    private boolean released;

    public PlaybackAudioFocusController(Context context, Handler handler, Callback callback) {
        this.callback = callback;
        behaviorProvider = UserPreferences::getAudioInterruptionBehavior;
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioManager.OnAudioFocusChangeListener listener = this::onAudioFocusChange;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusPlatform = new Api26AudioFocusPlatform(audioManager, handler, listener);
        } else {
            audioFocusPlatform = new CompatAudioFocusPlatform(audioManager, handler, listener);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static class Api26AudioFocusPlatform implements AudioFocusPlatform {
        private final AudioManager audioManager;
        private final AudioFocusRequest focusRequest;

        Api26AudioFocusPlatform(AudioManager audioManager, Handler handler,
                                AudioManager.OnAudioFocusChangeListener listener) {
            this.audioManager = audioManager;
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(listener, handler)
                    .build();
        }

        @Override
        public int requestFocus() {
            return audioManager.requestAudioFocus(focusRequest);
        }

        @Override
        public void abandonFocus() {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    private static class CompatAudioFocusPlatform implements AudioFocusPlatform {
        private final AudioManager audioManager;
        private final AudioFocusRequestCompat focusRequest;

        CompatAudioFocusPlatform(AudioManager audioManager, Handler handler,
                                 AudioManager.OnAudioFocusChangeListener listener) {
            this.audioManager = audioManager;
            AudioAttributesCompat attributes = new AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(listener, handler)
                    .build();
        }

        @Override
        public int requestFocus() {
            return AudioManagerCompat.requestAudioFocus(audioManager, focusRequest);
        }

        @Override
        public void abandonFocus() {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest);
        }
    }

    PlaybackAudioFocusController(AudioFocusPlatform audioFocusPlatform, Callback callback,
                                 BehaviorProvider behaviorProvider) {
        this.audioFocusPlatform = audioFocusPlatform;
        this.callback = callback;
        this.behaviorProvider = behaviorProvider;
    }

    public PlayRequestResult requestFocusForPlayback() {
        if (released || !callback.isLocalPlayback()) {
            return PlayRequestResult.PLAY_NOW;
        }
        if (focusRequestActive) {
            if (hasAudioFocus) {
                return PlayRequestResult.PLAY_NOW;
            }
            waitingForDelayedFocusGain = true;
            return PlayRequestResult.WAITING_FOR_FOCUS;
        }

        int result = audioFocusPlatform.requestFocus();
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequestActive = true;
            hasAudioFocus = true;
            waitingForDelayedFocusGain = false;
            return PlayRequestResult.PLAY_NOW;
        }
        if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            focusRequestActive = true;
            hasAudioFocus = false;
            waitingForDelayedFocusGain = true;
            return PlayRequestResult.WAITING_FOR_FOCUS;
        }
        focusRequestActive = false;
        hasAudioFocus = false;
        waitingForDelayedFocusGain = false;
        resumeOnFocusGain = false;
        return PlayRequestResult.DENIED;
    }

    public void onUserPause() {
        boolean cancelDelayedRequest = waitingForDelayedFocusGain;
        waitingForDelayedFocusGain = false;
        resumeOnFocusGain = false;
        if (cancelDelayedRequest) {
            abandonFocus();
        }
    }

    public void onPlaybackStopped() {
        restoreVolume();
        resumeOnFocusGain = false;
        waitingForDelayedFocusGain = false;
        abandonFocus();
    }

    public void onRemotePlaybackStarted() {
        onPlaybackStopped();
    }

    public void release() {
        restoreVolume();
        resumeOnFocusGain = false;
        waitingForDelayedFocusGain = false;
        abandonFocus();
        released = true;
    }

    void onAudioFocusChange(int focusChange) {
        if (released || !focusRequestActive || !callback.isLocalPlayback()) {
            return;
        }
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasAudioFocus = true;
                restoreVolume();
                boolean resumeInterruptedPlayback = resumeOnFocusGain;
                boolean startDelayedPlayback = waitingForDelayedFocusGain;
                resumeOnFocusGain = false;
                waitingForDelayedFocusGain = false;
                if (resumeInterruptedPlayback) {
                    callback.resumeAfterTransientAudioFocusLoss();
                } else if (startDelayedPlayback) {
                    callback.startAfterDelayedAudioFocusGain();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                hasAudioFocus = false;
                handleCanDuck();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                hasAudioFocus = false;
                pauseAndResumeLater();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                hasAudioFocus = false;
                restoreVolume();
                resumeOnFocusGain = false;
                waitingForDelayedFocusGain = false;
                callback.pauseForPermanentAudioFocusLoss();
                abandonFocus();
                break;
            default:
                break;
        }
    }

    private void handleCanDuck() {
        switch (behaviorProvider.getBehavior()) {
            case PAUSE:
                pauseAndResumeLater();
                break;
            case DUCK:
                callback.setFocusVolumeMultiplier(DUCK_VOLUME_MULTIPLIER);
                break;
            case KEEP_PLAYING:
                restoreVolume();
                break;
            default:
                break;
        }
    }

    private void pauseAndResumeLater() {
        restoreVolume();
        if (callback.getPlayWhenReady()) {
            resumeOnFocusGain = true;
            callback.pauseForTransientAudioFocusLoss();
        }
    }

    private void restoreVolume() {
        callback.setFocusVolumeMultiplier(1.0f);
    }

    private void abandonFocus() {
        if (!focusRequestActive) {
            return;
        }
        audioFocusPlatform.abandonFocus();
        focusRequestActive = false;
        hasAudioFocus = false;
    }

}
