package univ.flopbox.config;


import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO contenant les informations d'authentification pour un serveur spécifique.
 * Utilisé lors des requêtes de recherche globale ciblée.
 */
public record ServerCredentials (
        @JsonProperty("host") String host,
        @JsonProperty("username") String username,
        @JsonProperty("password") String password,
        @JsonProperty("alreadySynced") Boolean alreadySynced
) {
}
