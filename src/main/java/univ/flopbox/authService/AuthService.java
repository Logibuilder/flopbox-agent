package univ.flopbox.authService;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.model.LoginRequest;

import java.util.Objects;

public class AuthService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    public AuthService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Connecte l'utilisateur et sauvegarde le token JWT.
     *
     * @param mail     adresse email FlopBox
     * @param password mot de passe FlopBox
     */
    public boolean login(String mail, String password) {
        String token = api.login(new LoginRequest(mail, password));
        tokenStore.save(token);
        return !token.isEmpty();
    }
}
