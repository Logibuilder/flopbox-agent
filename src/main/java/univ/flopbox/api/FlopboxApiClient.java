package univ.flopbox.api;

import univ.flopbox.model.LoginRequest;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import univ.flopbox.utils.HttpUtils;

public class FlopboxApiClient implements FlopboxApi{


    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String BASE_URL = "http://localhost:8080/api/v1/auth";

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
            HttpRequest request = HttpUtils.createPostRequest(BASE_URL + "/login", jsonBody);
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
}
