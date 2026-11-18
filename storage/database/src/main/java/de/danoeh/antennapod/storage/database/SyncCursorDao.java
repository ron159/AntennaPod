package de.danoeh.antennapod.storage.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public final class SyncCursorDao {
    public boolean isInitialized() {
        return currentOrNull() != null;
    }

    public void initialize(String deviceId) {
        String normalizedDeviceId = requireDeviceId(deviceId);
        SQLiteDatabase database = database();
        CursorState current = currentOrNull();
        if (current == null) {
            ContentValues values = new ContentValues();
            values.put("id", 1);
            values.put("device_id", normalizedDeviceId);
            database.insertOrThrow(PodDBAdapter.TABLE_NAME_SYNC_CURSOR, null, values);
        } else if (!current.deviceId().equals(normalizedDeviceId)) {
            throw new IllegalStateException("StateSync device ID does not match the database");
        }
    }

    public CursorState current() {
        CursorState state = currentOrNull();
        if (state == null) {
            throw new IllegalStateException("StateSync cursor is not initialized");
        }
        return state;
    }

    public LocalVersion nextLocalVersion(long nowMilliseconds) {
        if (nowMilliseconds < 0) {
            throw new IllegalArgumentException("Clock value must not be negative");
        }
        CursorState current = current();
        if (current.lastLocalSequence() == Long.MAX_VALUE) {
            throw new IllegalStateException("StateSync local sequence overflow");
        }
        long wall = Math.max(nowMilliseconds, current.hlcWallMilliseconds());
        long counter = wall == current.hlcWallMilliseconds() ? current.hlcCounter() + 1 : 0;
        LocalVersion version = new LocalVersion(current.lastLocalSequence() + 1,
                wall, counter, current.deviceId());
        ContentValues values = new ContentValues();
        values.put("last_local_seq", version.localSequence());
        values.put("hlc_wall_ms", version.wallMilliseconds());
        values.put("hlc_counter", version.counter());
        database().update(PodDBAdapter.TABLE_NAME_SYNC_CURSOR, values, "id=1", null);
        return version;
    }

    public void observeRemote(long wallMilliseconds, long counter) {
        if (wallMilliseconds < 0 || counter < 0) {
            throw new IllegalArgumentException("Clock value must not be negative");
        }
        CursorState current = current();
        if (wallMilliseconds < current.hlcWallMilliseconds()
                || wallMilliseconds == current.hlcWallMilliseconds() && counter <= current.hlcCounter()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("hlc_wall_ms", wallMilliseconds);
        values.put("hlc_counter", counter);
        database().update(PodDBAdapter.TABLE_NAME_SYNC_CURSOR, values, "id=1", null);
    }

    public void advanceServerSequence(long serverSequence) {
        CursorState current = current();
        if (serverSequence < current.serverSequence()) {
            throw new IllegalArgumentException("StateSync cursor must not move backward");
        }
        ContentValues values = new ContentValues();
        values.put("server_seq", serverSequence);
        database().update(PodDBAdapter.TABLE_NAME_SYNC_CURSOR, values, "id=1", null);
    }

    public void resetServerSequence() {
        ContentValues values = new ContentValues();
        values.put("server_seq", 0);
        database().update(PodDBAdapter.TABLE_NAME_SYNC_CURSOR, values, "id=1", null);
    }

    private CursorState currentOrNull() {
        try (Cursor cursor = database().query(PodDBAdapter.TABLE_NAME_SYNC_CURSOR,
                new String[]{"server_seq", "last_local_seq", "device_id", "hlc_wall_ms", "hlc_counter"},
                "id=1", null, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new CursorState(cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                    cursor.getLong(3), cursor.getLong(4));
        }
    }

    private SQLiteDatabase database() {
        return PodDBAdapter.getInstance().getDatabase();
    }

    private static String requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("StateSync device ID is empty");
        }
        return deviceId.trim();
    }

    public record LocalVersion(long localSequence, long wallMilliseconds, long counter, String deviceId) {
    }

    public record CursorState(long serverSequence, long lastLocalSequence, String deviceId,
                              long hlcWallMilliseconds, long hlcCounter) {
        public LocalVersion localVersion() {
            return new LocalVersion(lastLocalSequence, hlcWallMilliseconds, hlcCounter, deviceId);
        }
    }
}
