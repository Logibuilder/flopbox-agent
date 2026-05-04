package univ.flopbox.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import univ.flopbox.model.Server;
import univ.flopbox.service.SyncService;
import univ.flopbox.utils.HttpUtils;

/**
 * Implémentation HTTP du contrat {@link FlopboxApi}.
 *
 * <p>Envoie les requêtes REST au proxy FlopBox et désérialise les réponses JSON.
 * Les transferts de fichiers (upload/download) sont effectués de manière asynchrone
 * via {@link java.net.http.HttpClient#sendAsync}.</p>
 */
public class FlopboxApiClient implements FlopboxApi {

    private static final Logger log = LoggerFactory.getLogger(FlopboxApiClient.class);

    private static final String BASE_URL = "http://localhost:8080/api/v1";
    private static final DateTimeFormatter FTP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Initialise le client avec un timeout de connexion de 10 secondes.
     */
    public FlopboxApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String login(LoginRequest loginRequest) {
        try {
            String jsonBody = objectMapper.writeValueAsString(loginRequest);
            HttpRequest request = HttpUtils.createPostRequest(BASE_URL + "/auth/login", jsonBody);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Echec connexion : HTTP " + response.statusCode());
            }

            return objectMapper.readTree(response.body())
                    .path("data")
                    .path("accessToken")
                    .asText();

        } catch (ConnectException e) {
            log.error("Impossible de contacter le serveur FlopBox : {}", e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de l'authentification : {}", e.getMessage());
        }
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Server> getServers(String token) {
        try {
            HttpRequest request = HttpUtils.createGetRequest(BASE_URL + "/servers", token);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Échec récupération serveurs : HTTP " + response.statusCode());
            }

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

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FtpItem> listDirectory(String token, String host, String path, String ftpUser, String ftpPassword) {
        try {
            String url = BASE_URL + "/servers/" + host + "/directories?path=" + path;
            HttpRequest request = HttpUtils.createGetRequest(url, token, ftpUser, ftpPassword);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Echec listDirectory : HTTP " + response.statusCode());
            }

            ApiResponse<List<FtpItem>> apiResponse = objectMapper.readValue(
                    response.body(), new TypeReference<>() {});
            return apiResponse.data();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erreur listDirectory sur " + host + path, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Le fichier est écrit dans le répertoire de synchronisation local via
     * {@link SyncService#createDirectory}. La date de modification locale est
     * ensuite alignée sur la date distante pour éviter de faux positifs lors
     * des cycles de synchronisation suivants.</p>
     */
    @Override
    public CompletableFuture<Void> downloadFile(String token, String host, FtpItem remoteFile, String ftpUser, String ftpPassword) {
        String rawPath = remoteFile.path().startsWith("/")
                ? remoteFile.path().substring(1)
                : remoteFile.path();

        String encodedPath = URLEncoder.encode(rawPath, StandardCharsets.UTF_8);
        String url = BASE_URL + "/servers/" + host + "/files?path=" + encodedPath;

        HttpRequest request = HttpUtils.createGetRequest(url, token, ftpUser, ftpPassword);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        log.warn("Téléchargement impossible [{}] HTTP {}", remoteFile.name(), response.statusCode());
                        return;
                    }

                    Path localPath = SyncService.createDirectory(host, remoteFile);

                    try (InputStream is = response.body()) {
                        // Écriture du fichier sur le disque
                        try {
                            Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
                            log.info("Fichier téléchargé : {}", localPath);
                        } catch (IOException e) {
                            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("connection")) {
                                log.warn("Interruption réseau pendant le téléchargement de {} : {}", remoteFile.name(), e.getMessage());
                            } else {
                                log.error("Écriture impossible sur disque [{}] : {}", localPath, e.getMessage());
                            }
                            return; // ne pas aligner la date si le fichier est corrompu
                        }

                        // Alignement de la date de modification locale sur la date distante
                        try {
                            long remoteTime = ZonedDateTime.parse(remoteFile.lastModified(), FTP_DATE_FORMAT)
                                    .toInstant().toEpochMilli();
                            Files.setLastModifiedTime(localPath, FileTime.fromMillis(remoteTime));
                            log.debug("Date alignée : {}", localPath.getFileName());
                        } catch (IOException e) {
                            log.warn("Alignement date impossible pour {} : {}", localPath.getFileName(), e.getMessage());
                        }

                    } catch (IOException e) {
                        log.error("Ouverture flux impossible pour {} : {}", remoteFile.name(), e.getMessage());
                    }
                })
                .exceptionally(ex -> {
                    log.error("Échec asynchrone pour {} : {}", remoteFile.name(), ex.getMessage());
                    return null;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Le contenu du fichier local est envoyé tel quel dans le corps de la requête POST.
     * Le chemin distant est passé en paramètre de l'URL.</p>
     */
    @Override
    public CompletableFuture<Void> uploadFile(String token, String host, String localPath, String remotePath, String ftpUser, String ftpPassword) throws FileNotFoundException {
        String url = BASE_URL + "/servers/" + host + "/files?path=" + remotePath;

        try {
            HttpRequest request = HttpUtils.createPostRequest(url, token, Path.of(localPath), ftpUser, ftpPassword);

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            log.info("Fichier uploadé : {}", remotePath);
                        } else {
                            log.warn("Upload échoué HTTP {} pour {}", response.statusCode(), remotePath);
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Erreur envoi de {} : {}", remotePath, ex.getMessage());
                        return null;
                    });

        } catch (FileNotFoundException e) {
            log.error("Fichier local introuvable pour upload : {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Préparation upload impossible : {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Envoie une requête DELETE au proxy FlopBox pour supprimer définitivement
     * le fichier au chemin {@code remotePath} sur le serveur FTP.</p>
     */
    @Override
    public CompletableFuture<Void> deleteFile(String token, String host, String remotePath, String ftpUser, String ftpPassword) {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String url = BASE_URL + "/servers/" + host + "/files?path=" + encodedPath;

        HttpRequest request = HttpUtils.createDeleteRequest(url, token, ftpUser, ftpPassword);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 204) {
                        log.info("Fichier supprimé sur le serveur : {}", remotePath);
                    } else {
                        log.warn("Suppression échouée HTTP {} pour {}", response.statusCode(), remotePath);
                    }
                })
                .exceptionally(ex -> {
                    log.error("Erreur async suppression de {} : {}", remotePath, ex.getMessage());
                    return null;
                });
    }


}