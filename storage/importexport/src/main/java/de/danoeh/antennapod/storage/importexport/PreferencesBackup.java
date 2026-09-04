package de.danoeh.antennapod.storage.importexport;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PreferencesBackup {
    static final String TABLE_NAME = "AntennaPodPreferences";

    private static final String COLUMN_KEY = "key";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_VALUE = "value";

    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_STRING_SET = "string_set";

    private PreferencesBackup() {
    }

    static void writeToDatabase(SQLiteDatabase database, SharedPreferences preferences) throws IOException {
        database.beginTransaction();
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + COLUMN_KEY + " TEXT PRIMARY KEY NOT NULL, "
                    + COLUMN_TYPE + " TEXT NOT NULL, "
                    + COLUMN_VALUE + " TEXT NOT NULL)");
            database.delete(TABLE_NAME, null, null);
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                ContentValues values = encode(entry.getKey(), entry.getValue());
                database.insertOrThrow(TABLE_NAME, null, values);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    static Map<String, Object> readAndRemoveFromDatabase(SQLiteDatabase database) throws IOException {
        if (!hasBackupTable(database)) {
            return null;
        }

        Map<String, Object> preferences = new LinkedHashMap<>();
        database.beginTransaction();
        try {
            try (Cursor cursor = database.query(TABLE_NAME,
                    new String[]{COLUMN_KEY, COLUMN_TYPE, COLUMN_VALUE},
                    null, null, null, null, COLUMN_KEY)) {
                int keyColumn = cursor.getColumnIndexOrThrow(COLUMN_KEY);
                int typeColumn = cursor.getColumnIndexOrThrow(COLUMN_TYPE);
                int valueColumn = cursor.getColumnIndexOrThrow(COLUMN_VALUE);
                while (cursor.moveToNext()) {
                    String key = cursor.getString(keyColumn);
                    String type = cursor.getString(typeColumn);
                    String value = cursor.getString(valueColumn);
                    preferences.put(key, decode(type, value));
                }
            }
            database.execSQL("DROP TABLE " + TABLE_NAME);
            database.setTransactionSuccessful();
        } catch (IllegalArgumentException | JSONException e) {
            throw new IOException("Invalid settings data in database backup", e);
        } finally {
            database.endTransaction();
        }
        return preferences;
    }

    static void restore(SharedPreferences preferences, Map<String, Object> values) throws IOException {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> stringSet = (Set<String>) value;
                editor.putStringSet(key, stringSet);
            } else {
                throw new IOException("Unsupported setting type for key: " + key);
            }
        }
        if (!editor.commit()) {
            throw new IOException("Unable to restore settings from database backup");
        }
    }

    private static boolean hasBackupTable(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{TABLE_NAME})) {
            return cursor.moveToFirst();
        }
    }

    private static ContentValues encode(String key, Object value) throws IOException {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY, key);
        if (value instanceof Boolean) {
            values.put(COLUMN_TYPE, TYPE_BOOLEAN);
            values.put(COLUMN_VALUE, value.toString());
        } else if (value instanceof Float) {
            values.put(COLUMN_TYPE, TYPE_FLOAT);
            values.put(COLUMN_VALUE, value.toString());
        } else if (value instanceof Integer) {
            values.put(COLUMN_TYPE, TYPE_INT);
            values.put(COLUMN_VALUE, value.toString());
        } else if (value instanceof Long) {
            values.put(COLUMN_TYPE, TYPE_LONG);
            values.put(COLUMN_VALUE, value.toString());
        } else if (value instanceof String) {
            values.put(COLUMN_TYPE, TYPE_STRING);
            values.put(COLUMN_VALUE, (String) value);
        } else if (value instanceof Set) {
            values.put(COLUMN_TYPE, TYPE_STRING_SET);
            values.put(COLUMN_VALUE, encodeStringSet(key, (Set<?>) value));
        } else {
            throw new IOException("Unsupported setting type for key: " + key);
        }
        return values;
    }

    private static String encodeStringSet(String key, Set<?> values) throws IOException {
        List<String> sortedValues = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String)) {
                throw new IOException("Unsupported setting type for key: " + key);
            }
            sortedValues.add((String) value);
        }
        sortedValues.sort(String::compareTo);
        return new JSONArray(sortedValues).toString();
    }

    private static Object decode(String type, String value) throws IOException, JSONException {
        try {
            switch (type) {
                case TYPE_BOOLEAN:
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw new IOException("Invalid boolean setting value");
                    }
                    return Boolean.parseBoolean(value);
                case TYPE_FLOAT:
                    return Float.parseFloat(value);
                case TYPE_INT:
                    return Integer.parseInt(value);
                case TYPE_LONG:
                    return Long.parseLong(value);
                case TYPE_STRING:
                    return value;
                case TYPE_STRING_SET:
                    JSONArray array = new JSONArray(value);
                    Set<String> stringSet = new HashSet<>();
                    for (int i = 0; i < array.length(); i++) {
                        stringSet.add(array.getString(i));
                    }
                    return stringSet;
                default:
                    throw new IOException("Unknown setting type: " + type);
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid numeric setting value", e);
        }
    }
}
