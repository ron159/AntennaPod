package de.danoeh.antennapod.storage.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public final class SyncEntityStateDao {
    public State get(String entityType, String entityKey) {
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_SYNC_ENTITY_STATES,
                new String[]{"payload_json", "hlc_wall_ms", "hlc_counter", "device_id", "intent_priority"},
                "entity_type=? AND entity_key=?", new String[]{entityType, entityKey},
                null, null, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new State(entityType, entityKey, cursor.getString(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getInt(4));
        }
    }

    public List<State> list(String entityType) {
        List<State> result = new ArrayList<>();
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_SYNC_ENTITY_STATES,
                new String[]{"entity_key", "payload_json", "hlc_wall_ms", "hlc_counter",
                        "device_id", "intent_priority"}, "entity_type=?", new String[]{entityType},
                null, null, null)) {
            while (cursor.moveToNext()) {
                result.add(new State(entityType, cursor.getString(0), cursor.getString(1), cursor.getLong(2),
                        cursor.getLong(3), cursor.getString(4), cursor.getInt(5)));
            }
        }
        return result;
    }

    public void put(State state) {
        ContentValues values = new ContentValues();
        values.put("entity_type", state.entityType());
        values.put("entity_key", state.entityKey());
        values.put("payload_json", state.payloadJson());
        values.put("hlc_wall_ms", state.wallMilliseconds());
        values.put("hlc_counter", state.counter());
        values.put("device_id", state.deviceId());
        values.put("intent_priority", state.intentPriority());
        database().insertWithOnConflict(PodDBAdapter.TABLE_NAME_SYNC_ENTITY_STATES, null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void clear() {
        database().delete(PodDBAdapter.TABLE_NAME_SYNC_ENTITY_STATES, null, null);
    }

    private SQLiteDatabase database() {
        return PodDBAdapter.getInstance().getDatabase();
    }

    public record State(String entityType, String entityKey, String payloadJson,
                        long wallMilliseconds, long counter, String deviceId, int intentPriority) {
    }
}
