package univ.flopbox.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import univ.flopbox.model.ApiResponse;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.LoginRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import univ.flopbox.model.Server;
import univ.flopbox.service.SyncService;
import univ.flopbox.utils.HttpUtils;

public class FlopboxApiClient implements FlopboxApi{


    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String BASE_URL = "http://localhost:8080/api/v1";
    /**
     * Initialise un nouveau client API avec une configuration par défaut.
     * Configure un délai d'attente de connexion de 10 secondes.
     */
    public FlopboxApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    @Override
    public String login(LoginRequest loginRequest) {
        try {
            String jsonBody = objectMapper.writeValueAsString(loginRequest);
            HttpRequest request = HttpUtils.createPostRequest(BASE_URL + "/auth/login", jsonBody);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Echec connexion : HTTP " + response.statusCode());
            }

            // Extraire le token depuis data.accessToken
            return objectMapper.readTree(response.body())
                    .path("data")
                    .path("accessToken")
                    .asText();

        } catch (ConnectException e) {
            System.out.println("Impossible de contacter le serveur FlopBox. Vérifiez qu'il est bien lancé");
        } catch (Exception e) {
            System.out.println("Erreur lors de l'authentification "+ e.getMessage());
        }
        return "";
    }

    @Override
    public List<Server> getServers(String token) {
        try {
            HttpRequest request = HttpUtils.createGetRequest(BASE_URL + "/servers", token);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Échec récupération serveurs : HTTP " + response.statusCode());
            }

            // La réponse a la forme { code, message, data: [...] }
            JsonNode dataNode = objectMapper.readTree(response.body()).path("data");

            return objectMapper.readValue(
                    dataNode.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Server.class)
            );

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la récupération des serveurs", e);
        }
    }


    public List<FtpItem> listDirectory(String token, String host, String path, String ftpUser, String ftpPassword) {
        try {
            String url = BASE_URL + "/servers/" + host + "/directories?path=" + path;
            HttpRequest request = HttpUtils.createGetRequest(url, token, ftpUser, ftpPassword);

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Echec listDirectory : HTTP " + response.statusCode());
            }

            ApiResponse<List<FtpItem>> apiResponse = objectMapper.readValue(
                    response.body(),
                    new TypeReference<>() {});

            return apiResponse.data();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erreur listDirectory sur " + host + path, e);
        }
    }

    public CompletableFuture<Void> downloadFile(String token, String host, FtpItem remoteFile, String ftpUser, String ftpPassword) {

        String rawPath = remoteFile.path();
        if (rawPath.startsWith("/")) {
            rawPath = rawPath.substring(1);
        }

        // Encodage du chemin propre
        String encodedPath = URLEncoder.encode(rawPath, StandardCharsets.UTF_8);
        String url = BASE_URL + "/servers/" + host + "/files?path=" + encodedPath;

        HttpRequest request = HttpUtils.createGetRequest(url, token, ftpUser, ftpPassword);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("[ERREUR] Impossible de télécharger " + remoteFile.name() + " : HTTP " + response.statusCode());
                        return;
                    }
                    Path localPath = SyncService.createDirectory(host, remoteFile);

                    try (InputStream is = response.body()) {
                        Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("[OK] Terminé : " + localPath);
                    } catch (IOException e) {
                        System.err.println("[ERREUR DISQUE] " + localPath + " : " + e.getMessage());
                    }

                })
                .exceptionally(ex -> {
                    System.err.println("[ERREUR CRITIQUE] Échec asynchrone pour " + remoteFile.name() + " : " + ex.getMessage());
                    return null;
                });

    }

    @Override
    public CompletableFuture<Void> uploadFile(String token, String host, String localPath,String remotePath, String ftpUser, String ftpPassword) throws  FileNotFoundException{
        String url = BASE_URL + "/servers/" + host + "/files?path=" + remotePath;


        try {

            HttpRequest request = HttpUtils.createPostRequest(url, token, Path.of(localPath), ftpUser, ftpPassword);

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            System.out.println("[OK] Fichier envoyé avec succès : " + remotePath);
                        } else {
                            System.out.println("Échec de l'envoi (HTTP " + response.statusCode() + ")");
                        }
                    })
                    .exceptionally(ex -> {
                        System.out.println("Erreur lors de l'envoi de " + remotePath + " : " + ex.getMessage());
                        return null;
                    });
        }catch (FileNotFoundException e) {
            System.out.println("Erreur lors de la préparation de l'envoi : " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            System.out.println("Erreur lors de la préparation de l'envoi  : " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}
