package univ.flopbox.utils;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

public class HttpUtils {


    private  static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    public static HttpRequest createPostRequest(String url, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    public static HttpRequest createGetRequest(String url, String bearerToken) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
    }

    public static HttpRequest createGetRequest(String url, String bearerToken, String ftpUser,String ftpPassword) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .header("X-FTP-Username", ftpUser)
                .header("X-FTP-Password", ftpPassword)
                .GET()
                .build();
    }
}
