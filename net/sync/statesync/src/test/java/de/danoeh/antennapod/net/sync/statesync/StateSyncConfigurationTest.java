package de.danoeh.antennapod.net.sync.statesync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StateSyncConfigurationTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    public void secureAndLoopbackUrlsMatchTheIosRules() {
        StateSyncConfiguration secure = new StateSyncConfiguration("https://nas.example.com/sync/", TOKEN);
        StateSyncConfiguration loopback = new StateSyncConfiguration("http://127.0.0.1:8080", TOKEN);

        assertEquals("https://nas.example.com/sync", secure.serverUrl());
        assertEquals("https://nas.example.com/sync/v1/sync", secure.endpoint().toString());
        assertEquals("http://127.0.0.1:8080", loopback.serverUrl());
    }

    @Test
    public void rejectsInsecureRemoteUrlsAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> new StateSyncConfiguration("http://nas.example.com", TOKEN));
        assertThrows(IllegalArgumentException.class,
                () -> new StateSyncConfiguration("https://user:password@nas.example.com", TOKEN));
        assertThrows(IllegalArgumentException.class,
                () -> new StateSyncConfiguration("https://nas.example.com?token=bad", TOKEN));
    }

    @Test
    public void tokenUsesTheIosLengthAndCharacterWhitelist() {
        assertThrows(IllegalArgumentException.class,
                () -> new StateSyncConfiguration("https://nas.example.com", "short"));
        assertThrows(IllegalArgumentException.class,
                () -> new StateSyncConfiguration("https://nas.example.com", TOKEN.substring(1) + "!"));
    }
}
