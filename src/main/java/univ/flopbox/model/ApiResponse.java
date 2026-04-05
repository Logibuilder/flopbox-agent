package univ.flopbox.model;

/**
 * Enveloppe générique pour uniformiser toutes les réponses de l'API REST.
 * Côté client, ce record permet de désérialiser les réponses du serveur.
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data
) {
    // Méthodes statiques pour faciliter la création d'objets si besoin de tests
    public static <T> ApiResponse<T> success(int code, T data, String message) {
        return new ApiResponse<>(code, message, data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}