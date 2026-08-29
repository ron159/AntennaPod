package de.danoeh.antennapod.net.sync.statesync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CanonicalIdentityTest {
    @Test
    public void feedKeyNormalizesNetworkUrl() {
        assertEquals("69bd18a623128846d3fa8bec39cbe0cb2edbd99bb36cc337ff367da0a3ebe1b7",
                CanonicalIdentity.feedKey("HTTPS://Example.COM:443/feed.xml?b=2&a=1#fragment"));
        assertEquals(CanonicalIdentity.feedKey("https://example.com/"),
                CanonicalIdentity.feedKey("https://example.com"));
    }

    @Test
    public void episodeKeyUsesTheIosLocatorPriority() {
        String feedUrl = "https://example.com/feed";
        assertEquals("f5f510cf573a5c3138d0a0cb054f0148262c34fd26779398563136f96cbb9cdf",
                CanonicalIdentity.episodeKey(feedUrl, "  episode-guid  ",
                        "HTTPS://CDN.Example.com:443/audio.mp3#part",
                        "https://example.com/episode", 1_700_000_000_000L,
                        "Episode One", 3_600_000L));
        assertEquals("c90fbc407dedcb6b62d92506520a86519c4a0b8a680df4c8d15c8699b17e9af8",
                CanonicalIdentity.episodeKey(feedUrl, null, null, null,
                        1_700_000_000_000L, "  EPISODE\n One  ", 100L));
    }

    @Test
    public void rejectsUnsupportedOrIncompleteLocators() {
        assertThrows(IllegalArgumentException.class, () -> CanonicalIdentity.feedKey("file:///tmp/feed.xml"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalIdentity.episodeKey(
                "https://example.com/feed", null, null, null, null, "Missing date", null));
    }
}
