package univ.flopbox.config;


/**
 * DTO contenant les informations d'authentification pour un serveur spécifique.
 * Utilisé lors des requêtes de recherche globale ciblée.
 */
public record ServerCredentials(
        String host,
        String username,
        String password
) {
}
