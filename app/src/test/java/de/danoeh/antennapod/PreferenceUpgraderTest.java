package de.danoeh.antennapod;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences.AudioInterruptionBehavior;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class PreferenceUpgraderTest {
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().clear().commit();
        UserPreferences.init(context);
    }

    @Test
    public void migratesLegacyPauseValue() {
        preferences.edit().putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true).commit();

        PreferenceUpgrader.migrateAudioInterruptionBehavior(preferences);

        assertEquals(AudioInterruptionBehavior.PAUSE, UserPreferences.getAudioInterruptionBehavior());
    }

    @Test
    public void migratesLegacyDuckValue() {
        preferences.edit().putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, false).commit();

        PreferenceUpgrader.migrateAudioInterruptionBehavior(preferences);

        assertEquals(AudioInterruptionBehavior.DUCK, UserPreferences.getAudioInterruptionBehavior());
    }

    @Test
    public void defaultsToPauseWithoutLegacyValue() {
        PreferenceUpgrader.migrateAudioInterruptionBehavior(preferences);

        assertEquals(AudioInterruptionBehavior.PAUSE, UserPreferences.getAudioInterruptionBehavior());
    }

    @Test
    public void preservesExistingInterruptionBehavior() {
        preferences.edit()
                .putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true)
                .putString(UserPreferences.PREF_AUDIO_INTERRUPTION_BEHAVIOR, "keep_playing")
                .commit();

        PreferenceUpgrader.migrateAudioInterruptionBehavior(preferences);

        assertEquals(AudioInterruptionBehavior.KEEP_PLAYING, UserPreferences.getAudioInterruptionBehavior());
    }
}
