package de.danoeh.antennapod.net.sync.statesync;

import android.content.Context;
import android.content.SharedPreferences;

final class StateSyncSettingsStore {
    private static final String PREFS_NAME = "statesync_settings";
    private static final String PREF_SERVER_URL = "server_url";
    private static final String PREF_BOUND_SERVER_URL = "bound_server_url";
    private static final String PREF_BOOTSTRAPPED = "bootstrapped";

    private final SharedPreferences preferences;
    private final AuthTokenStore tokenStore;

    StateSyncSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        tokenStore = new AuthTokenStore(context);
    }

    void save(StateSyncConfiguration configuration, boolean resetBootstrap) {
        tokenStore.save(configuration.token());
        SharedPreferences.Editor editor = preferences.edit()
                .putString(PREF_SERVER_URL, configuration.serverUrl())
                .putString(PREF_BOUND_SERVER_URL, configuration.serverUrl());
        if (resetBootstrap) {
            editor.putBoolean(PREF_BOOTSTRAPPED, false);
        }
        editor.apply();
    }

    StateSyncConfiguration load() {
        String serverUrl = preferences.getString(PREF_SERVER_URL, null);
        String token = tokenStore.load();
        if (serverUrl == null && token == null) {
            return null;
        }
        if (serverUrl == null || token == null) {
            throw new IllegalStateException("StateSync configuration is incomplete");
        }
        return new StateSyncConfiguration(serverUrl, token);
    }

    void clear() {
        tokenStore.clear();
        preferences.edit().remove(PREF_SERVER_URL).apply();
    }

    String serverUrl() {
        return preferences.getString(PREF_SERVER_URL, "");
    }

    String boundServerUrl() {
        return preferences.getString(PREF_BOUND_SERVER_URL, "");
    }

    boolean isBootstrapped() {
        return preferences.getBoolean(PREF_BOOTSTRAPPED, false);
    }

    void setBootstrapped() {
        preferences.edit().putBoolean(PREF_BOOTSTRAPPED, true).apply();
    }
}
