package de.danoeh.antennapod.storage.database;

import android.database.sqlite.SQLiteDatabase;

public final class StateSyncDatabase {
    private StateSyncDatabase() {
    }

    public static void runInTransaction(Runnable action) {
        SQLiteDatabase database = PodDBAdapter.getInstance().getDatabase();
        database.beginTransactionNonExclusive();
        try {
            action.run();
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
}
