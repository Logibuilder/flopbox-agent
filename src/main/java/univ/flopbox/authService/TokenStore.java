package univ.flopbox.authService;

/**
 * Stockage en mémoire du token JWT pour la durée de la session.
 *
 * <p>Le token est obtenu après un appel réussi à {@link AuthService#login}
 * et doit être fourni à chaque requête vers la plateforme FlopBox.</p>
 */
public class TokenStore {

    private String token;

    /**
     * Sauvegarde le token JWT de session.
     *
     * @param token le token JWT retourné par le serveur FlopBox
     */
    public void save(String token) {
        this.token = token;
    }

    /**
     * Retourne le token JWT courant.
     *
     * @return le token JWT, ou {@code null} si aucune connexion n'a encore eu lieu
     */
    public String get() {
        return token;
    }

    /**
     * Indique si un token valide est disponible.
     *
     * @return {@code true} si un token non nul et non vide est stocké, {@code false} sinon
     */
    public boolean hasToken() {
        return token != null && !token.isEmpty(); // correction : null check en premier
    }
}