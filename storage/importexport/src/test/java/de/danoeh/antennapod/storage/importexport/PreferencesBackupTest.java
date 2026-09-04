package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PreferencesBackupTest {
    private static final String DATABASE_NAME = "preferences-backup-test.db";
    private static final String SOURCE_PREFERENCES = "preferences-backup-source";
    private static final String TARGET_PREFERENCES = "preferences-backup-target";

    private Context context;
    private SQLiteDatabase database;
    private SharedPreferences sourcePreferences;
    private SharedPreferences targetPreferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
        database = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        sourcePreferences = context.getSharedPreferences(SOURCE_PREFERENCES, Context.MODE_PRIVATE);
        targetPreferences = context.getSharedPreferences(TARGET_PREFERENCES, Context.MODE_PRIVATE);
        sourcePreferences.edit().clear().commit();
        targetPreferences.edit().clear().commit();
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase(DATABASE_NAME);
        sourcePreferences.edit().clear().commit();
        targetPreferences.edit().clear().commit();
    }

    @Test
    public void writeAndReadPreservesEverySharedPreferencesType() throws Exception {
        sourcePreferences.edit()
                .putBoolean("boolean", true)
                .putFloat("float", 1.25f)
                .putInt("int", 42)
                .putLong("long", 9876543210L)
                .putString("string", "value")
                .putStringSet("set", new HashSet<>(Arrays.asList("one", "two")))
                .commit();

        PreferencesBackup.writeToDatabase(database, sourcePreferences);
        Map<String, Object> restoredValues = PreferencesBackup.readAndRemoveFromDatabase(database);

        assertEquals(sourcePreferences.getAll(), restoredValues);
        assertFalse(hasPreferencesBackupTable());
    }

    @Test
    public void restoreReplacesExistingPreferences() throws Exception {
        sourcePreferences.edit().putString("new-key", "new-value").commit();
        targetPreferences.edit().putString("old-key", "old-value").commit();
        PreferencesBackup.writeToDatabase(database, sourcePreferences);

        Map<String, Object> restoredValues = PreferencesBackup.readAndRemoveFromDatabase(database);
        PreferencesBackup.restore(targetPreferences, restoredValues);

        assertEquals(sourcePreferences.getAll(), targetPreferences.getAll());
        assertFalse(targetPreferences.contains("old-key"));
    }

    @Test
    public void emptySettingsSnapshotClearsExistingPreferences() throws Exception {
        targetPreferences.edit().putString("old-key", "old-value").commit();
        PreferencesBackup.writeToDatabase(database, sourcePreferences);

        Map<String, Object> restoredValues = PreferencesBackup.readAndRemoveFromDatabase(database);
        PreferencesBackup.restore(targetPreferences, restoredValues);

        assertTrue(restoredValues.isEmpty());
        assertTrue(targetPreferences.getAll().isEmpty());
    }

    @Test
    public void readOldBackupWithoutPreferencesLeavesSettingsUntouched() throws Exception {
        targetPreferences.edit().putString("existing", "value").commit();

        Map<String, Object> restoredValues = PreferencesBackup.readAndRemoveFromDatabase(database);

        assertNull(restoredValues);
        assertTrue(targetPreferences.contains("existing"));
    }

    private boolean hasPreferencesBackupTable() {
        try (Cursor cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{PreferencesBackup.TABLE_NAME})) {
            return cursor.moveToFirst();
        }
    }
}
