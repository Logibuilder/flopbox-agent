package univ.flopbox.utils;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
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

    public static HttpRequest createPostRequest(String url, String bearerToken, Path path, String ftpUser, String ftpPassword ) throws FileNotFoundException {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("X-FTP-Username", ftpUser)
                .header("X-FTP-Password", ftpPassword)
                .POST(HttpRequest.BodyPublishers.ofFile(path))
                .build();
    }


    /**
     * Crée une requête HTTP DELETE pour supprimer un fichier sur le serveur FTP distant.
     *
     * <p>Les credentials FTP sont transmis via des headers personnalisés.
     * Le chemin du fichier à supprimer est passé en paramètre de l'URL.</p>
     *
     * @param url         l'URL de destination de la requête (contient le chemin du fichier)
     * @param bearerToken le token JWT de l'utilisateur authentifié
     * @param ftpUser     le nom d'utilisateur FTP (header {@code X-FTP-Username})
     * @param ftpPassword le mot de passe FTP (header {@code X-FTP-Password})
     * @return la requête HTTP DELETE configurée
     */
    public static HttpRequest createDeleteRequest(String url, String bearerToken, String ftpUser, String ftpPassword) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("X-FTP-Username", ftpUser)
                .header("X-FTP-Password", ftpPassword)
                .DELETE()
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

    /**
     * Crée une requête HTTP PATCH avec un corps JSON.
     *
     * @param url         l'URL de destination
     * @param bearerToken le token JWT de l'utilisateur authentifié
     * @param jsonBody    le corps JSON de la requête
     * @param ftpUser     le nom d'utilisateur FTP (header {@code X-FTP-Username})
     * @param ftpPassword le mot de passe FTP (header {@code X-FTP-Password})
     * @return la requête HTTP PATCH configurée
     */
    public static HttpRequest createPatchRequest(String url, String bearerToken, String jsonBody, String ftpUser, String ftpPassword) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .header("X-FTP-Username", ftpUser)
                .header("X-FTP-Password", ftpPassword)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }
}
