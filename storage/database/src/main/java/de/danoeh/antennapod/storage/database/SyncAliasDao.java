package de.danoeh.antennapod.storage.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public final class SyncAliasDao {
    private static final String ANDROID_PREFIX = "android:";

    public void linkLocal(String entityKind, long localId, String canonicalKey) {
        put(ANDROID_PREFIX + entityKind + ":" + localId, canonicalKey, entityKind);
    }

    public void put(String aliasKey, String canonicalKey, String entityKind) {
        if (aliasKey == null || aliasKey.isBlank() || canonicalKey == null || canonicalKey.isBlank()
                || !entityKind.equals("feed") && !entityKind.equals("episode")) {
            throw new IllegalArgumentException("StateSync alias is invalid");
        }
        String existing = canonicalFor(aliasKey, entityKind);
        if (existing != null && !existing.equals(canonicalKey)) {
            throw new IllegalStateException("StateSync alias already points to another entity");
        }
        ContentValues values = new ContentValues();
        values.put("alias_key", aliasKey);
        values.put("canonical_key", canonicalKey);
        values.put("entity_kind", entityKind);
        values.put("updated_at_ms", System.currentTimeMillis());
        database().insertWithOnConflict(PodDBAdapter.TABLE_NAME_SYNC_ALIASES, null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String canonicalFor(String aliasKey, String entityKind) {
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_SYNC_ALIASES,
                new String[]{"canonical_key"}, "alias_key=? AND entity_kind=?",
                new String[]{aliasKey, entityKind}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public Long localId(String entityKind, String canonicalKey) {
        String prefix = ANDROID_PREFIX + entityKind + ":";
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_SYNC_ALIASES,
                new String[]{"alias_key"}, "canonical_key=? AND entity_kind=? AND alias_key LIKE ?",
                new String[]{canonicalKey, entityKind, prefix + "%"}, null, null, "updated_at_ms DESC", "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            try {
                return Long.parseLong(cursor.getString(0).substring(prefix.length()));
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("StateSync local alias is corrupt", exception);
            }
        }
    }

    private SQLiteDatabase database() {
        return PodDBAdapter.getInstance().getDatabase();
    }
}
