package univ.flopbox.authService;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.model.LoginRequest;



/**
 * Service d'authentification à la plateforme FlopBox.
 *
 * <p>Délègue l'appel de connexion à {@link FlopboxApi} et stocke
 * le token JWT retourné dans le {@link TokenStore} pour les requêtes suivantes.</p>
 */
public class AuthService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;


    /**
     * @param api        client HTTP FlopBox utilisé pour l'appel de connexion
     * @param tokenStore stockage en mémoire du token JWT de session
     */
    public AuthService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Authentifie l'utilisateur sur la plateforme FlopBox et sauvegarde le token JWT.
     *
     * @param mail     adresse email du compte FlopBox
     * @param password mot de passe du compte FlopBox
     * @return {@code true} si l'authentification a réussi et qu'un token a été obtenu,
     *         {@code false} sinon
     */
    public boolean login(String mail, String password) {
        String token = api.login(new LoginRequest(mail, password));
        tokenStore.save(token);
        return token != null && !token.isEmpty();
    }
}
