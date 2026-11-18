package de.danoeh.antennapod.net.sync.statesync;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class StateSyncClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;

    StateSyncClient() {
        client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS).build();
    }

    SyncResponse synchronize(String requestJson, StateSyncConfiguration configuration) throws IOException {
        byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);
        if (requestBytes.length > StateSyncRepository.MAXIMUM_BODY_BYTES) {
            throw new StateSyncHttpException(StateSyncHttpException.Reason.INVALID_RESPONSE,
                    "StateSync request exceeds the size limit");
        }
        HttpUrl url = configuration.endpoint();
        for (int redirects = 0; redirects <= 5; redirects++) {
            Request request = new Request.Builder().url(url)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + configuration.token())
                    .post(RequestBody.create(requestBytes, JSON)).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    HttpUrl redirected = location == null ? null : url.resolve(location);
                    if (redirected == null || !sameOrigin(configuration.endpoint(), redirected) || redirects == 5) {
                        throw new StateSyncHttpException(StateSyncHttpException.Reason.INVALID_RESPONSE,
                                "StateSync redirect changed origin or exceeded the limit");
                    }
                    url = redirected;
                    continue;
                }
                if (response.code() == 401 || response.code() == 403) {
                    throw new StateSyncHttpException(StateSyncHttpException.Reason.AUTHENTICATION,
                            "StateSync authentication failed");
                }
                if (!response.isSuccessful()) {
                    throw new StateSyncHttpException(StateSyncHttpException.Reason.SERVER,
                            "StateSync server returned HTTP " + response.code());
                }
                if (!isJson(response.header("Content-Type"))) {
                    throw new StateSyncHttpException(StateSyncHttpException.Reason.INVALID_RESPONSE,
                            "StateSync response is not JSON");
                }
                ResponseBody body = response.body();
                if (body == null || body.contentLength() > StateSyncRepository.MAXIMUM_BODY_BYTES) {
                    throw new StateSyncHttpException(StateSyncHttpException.Reason.INVALID_RESPONSE,
                            "StateSync response exceeds the size limit");
                }
                String responseJson = readLimited(body.byteStream());
                try {
                    return SyncResponse.decode(responseJson);
                } catch (JSONException | IllegalArgumentException exception) {
                    throw new StateSyncHttpException(StateSyncHttpException.Reason.INVALID_RESPONSE,
                            "StateSync response is invalid");
                }
            }
        }
        throw new StateSyncHttpException(StateSyncHttpException.Reason.INVALID_RESPONSE,
                "StateSync redirect limit exceeded");
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > StateSyncRepository.MAXIMUM_BODY_BYTES) {
                throw new StateSyncHttpException(StateSyncHttpException.Reason.INVALID_RESPONSE,
                        "StateSync response exceeds the size limit");
            }
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.US);
        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }

    private static boolean sameOrigin(HttpUrl left, HttpUrl right) {
        return left.scheme().equalsIgnoreCase(right.scheme())
                && left.host().equalsIgnoreCase(right.host()) && left.port() == right.port();
    }
}
