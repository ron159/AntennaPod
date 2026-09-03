package de.danoeh.antennapod.storage.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public final class SyncOutboxDao {
    public void put(SyncCursorDao.LocalVersion version, String entityType,
                    String entityKey, String envelopeJson) {
        validateEntry(entityType, entityKey, envelopeJson);
        deleteEntity(version.deviceId(), entityType, entityKey);
        append(version, entityType, entityKey, envelopeJson);
    }

    public void append(SyncCursorDao.LocalVersion version, String entityType,
                       String entityKey, String envelopeJson) {
        validateEntry(entityType, entityKey, envelopeJson);
        ContentValues values = new ContentValues();
        values.put("device_id", version.deviceId());
        values.put("local_seq", version.localSequence());
        values.put("hlc_wall_ms", version.wallMilliseconds());
        values.put("hlc_counter", version.counter());
        values.put("entity_type", entityType);
        values.put("entity_key", entityKey);
        values.put("envelope_json", envelopeJson);
        values.put("created_at_ms", System.currentTimeMillis());
        database().insertOrThrow(PodDBAdapter.TABLE_NAME_SYNC_OUTBOX, null, values);
    }

    public List<Entry> pendingForEntity(String deviceId, String entityType, String entityKey) {
        List<Entry> result = new ArrayList<>();
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_SYNC_OUTBOX,
                new String[]{"device_id", "local_seq", "hlc_wall_ms", "hlc_counter",
                        "entity_type", "entity_key", "envelope_json"},
                "device_id=? AND entity_type=? AND entity_key=?",
                new String[]{deviceId, entityType, entityKey}, null, null, "local_seq ASC")) {
            while (cursor.moveToNext()) {
                result.add(new Entry(cursor.getString(0), cursor.getLong(1), cursor.getLong(2),
                        cursor.getLong(3), cursor.getString(4), cursor.getString(5), cursor.getString(6)));
            }
        }
        return result;
    }

    public void delete(String deviceId, long localSequence) {
        database().delete(PodDBAdapter.TABLE_NAME_SYNC_OUTBOX,
                "device_id=? AND local_seq=?", new String[]{deviceId, String.valueOf(localSequence)});
    }

    public List<Entry> pending(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("StateSync batch limit must be between 1 and 500");
        }
        List<Entry> result = new ArrayList<>();
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_SYNC_OUTBOX,
                new String[]{"device_id", "local_seq", "hlc_wall_ms", "hlc_counter",
                        "entity_type", "entity_key", "envelope_json"},
                null, null, null, null, "local_seq ASC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                result.add(new Entry(cursor.getString(0), cursor.getLong(1), cursor.getLong(2),
                        cursor.getLong(3), cursor.getString(4), cursor.getString(5), cursor.getString(6)));
            }
        }
        return result;
    }

    public long count() {
        try (Cursor cursor = database().rawQuery(
                "SELECT COUNT(*) FROM " + PodDBAdapter.TABLE_NAME_SYNC_OUTBOX, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public void deleteAccepted(String deviceId, long acceptedLocalSequence) {
        if (acceptedLocalSequence < 0) {
            throw new IllegalArgumentException("Accepted local sequence must not be negative");
        }
        database().delete(PodDBAdapter.TABLE_NAME_SYNC_OUTBOX,
                "device_id=? AND local_seq<=?", new String[]{deviceId, String.valueOf(acceptedLocalSequence)});
    }

    public void clear() {
        database().delete(PodDBAdapter.TABLE_NAME_SYNC_OUTBOX, null, null);
    }

    private void deleteEntity(String deviceId, String entityType, String entityKey) {
        database().delete(PodDBAdapter.TABLE_NAME_SYNC_OUTBOX,
                "device_id=? AND entity_type=? AND entity_key=?",
                new String[]{deviceId, entityType, entityKey});
    }

    private static void validateEntry(String entityType, String entityKey, String envelopeJson) {
        if (entityType == null || entityType.isBlank() || entityKey == null || entityKey.isBlank()
                || envelopeJson == null || envelopeJson.isBlank()) {
            throw new IllegalArgumentException("StateSync outbox entry is incomplete");
        }
    }

    private SQLiteDatabase database() {
        return PodDBAdapter.getInstance().getDatabase();
    }

    public record Entry(String deviceId, long localSequence, long wallMilliseconds, long counter,
                        String entityType, String entityKey, String envelopeJson) {
    }
}
