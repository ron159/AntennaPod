package de.danoeh.antennapod.net.sync.statesync;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import okhttp3.HttpUrl;

public final class StateSyncConfiguration {
    private final HttpUrl serverUrl;
    private final String token;

    public StateSyncConfiguration(String serverUrl, String token) {
        HttpUrl parsed;
        try {
            parsed = HttpUrl.get(serverUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid StateSync server URL", exception);
        }
        String scheme = parsed.scheme().toLowerCase(Locale.ROOT);
        String host = parsed.host().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || host.isBlank()
                || !parsed.username().isEmpty() || !parsed.password().isEmpty()
                || parsed.query() != null || parsed.fragment() != null) {
            throw new IllegalArgumentException("Invalid StateSync server URL");
        }
        if (scheme.equals("http") && !isLoopback(host)) {
            throw new IllegalArgumentException("StateSync requires HTTPS outside loopback");
        }
        HttpUrl normalized = parsed;
        while (normalized.encodedPath().length() > 1 && normalized.encodedPath().endsWith("/")) {
            normalized = normalized.newBuilder().removePathSegment(normalized.pathSize() - 1).build();
        }
        validateToken(token);
        this.serverUrl = normalized;
        this.token = token;
    }

    public String serverUrl() {
        String value = serverUrl.toString();
        return value.endsWith("/") && serverUrl.encodedPath().equals("/")
                ? value.substring(0, value.length() - 1) : value;
    }

    public HttpUrl endpoint() {
        return serverUrl.newBuilder().addPathSegment("v1").addPathSegment("sync").build();
    }

    public String token() {
        return token;
    }

    @Override
    public String toString() {
        return "StateSyncConfiguration{serverUrl=" + serverUrl() + ", token=<redacted>}";
    }

    private static void validateToken(String token) {
        if (token == null || token.getBytes(StandardCharsets.UTF_8).length < 32
                || token.getBytes(StandardCharsets.UTF_8).length > 4096
                || !token.matches("[A-Za-z0-9\\-._~+/=]+")) {
            throw new IllegalArgumentException("Invalid StateSync token");
        }
    }

    private static boolean isLoopback(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }
}
