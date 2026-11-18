package de.danoeh.antennapod.net.sync.statesync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.HttpUrl;

public final class CanonicalIdentity {
    private CanonicalIdentity() {
    }

    public static String feedKey(String url) {
        return sha256("feed:v1:" + normalizeUrl(url));
    }

    public static String episodeKey(String feedUrl, String guid, String enclosureUrl, String linkUrl,
                                    Long publicationDateMilliseconds, String title, Long durationMilliseconds) {
        String locator;
        if (guid != null && !guid.trim().isEmpty()) {
            locator = guid.trim();
        } else if (enclosureUrl != null) {
            locator = normalizeUrl(enclosureUrl);
        } else if (linkUrl != null && publicationDateMilliseconds != null) {
            locator = normalizeUrl(linkUrl) + "|" + publicationDateMilliseconds;
        } else if (title != null && publicationDateMilliseconds != null
                && durationMilliseconds != null && durationMilliseconds >= 0) {
            String normalizedTitle = normalizeTitle(title);
            if (normalizedTitle.isEmpty()) {
                throw new IllegalArgumentException("Episode locator is incomplete");
            }
            locator = normalizedTitle + "|" + publicationDateMilliseconds + "|" + durationMilliseconds;
        } else {
            throw new IllegalArgumentException("Episode locator is incomplete");
        }
        return sha256("episode:v1:" + feedKey(feedUrl) + ":" + locator);
    }

    static String normalizeUrl(String value) {
        HttpUrl url;
        try {
            url = HttpUrl.get(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid network URL", exception);
        }
        if (!url.scheme().equals("http") && !url.scheme().equals("https")) {
            throw new IllegalArgumentException("Unsupported URL scheme");
        }
        List<QueryItem> queryItems = new ArrayList<>();
        for (int index = 0; index < url.querySize(); index++) {
            queryItems.add(new QueryItem(url.queryParameterName(index), url.queryParameterValue(index)));
        }
        Collections.sort(queryItems, (left, right) -> {
            int nameComparison = left.name().compareTo(right.name());
            if (nameComparison != 0) {
                return nameComparison;
            }
            String leftValue = left.value() == null ? "" : left.value();
            String rightValue = right.value() == null ? "" : right.value();
            return leftValue.compareTo(rightValue);
        });
        HttpUrl.Builder queryBuilder = new HttpUrl.Builder().scheme(url.scheme()).host(url.host());
        for (QueryItem item : queryItems) {
            queryBuilder.addQueryParameter(item.name(), item.value());
        }
        String host = url.host().contains(":") ? "[" + url.host() + "]" : url.host();
        String port = url.port() == HttpUrl.defaultPort(url.scheme()) ? "" : ":" + url.port();
        String path = url.encodedPath().equals("/") ? "" : url.encodedPath();
        String query = queryItems.isEmpty() ? "" : "?" + queryBuilder.build().encodedQuery();
        return url.scheme() + "://" + host + port + path + query;
    }

    private static String normalizeTitle(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) {
            return "";
        }
        return String.join(" ", normalized.split("\\s+")).toLowerCase(Locale.US);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.US, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record QueryItem(String name, String value) {
    }
}
