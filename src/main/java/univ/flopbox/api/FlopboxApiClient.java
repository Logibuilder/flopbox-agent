package univ.flopbox.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import univ.flopbox.model.ApiResponse;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.LoginRequest;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import univ.flopbox.model.Server;
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

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'authentification", e);
        }
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
}
