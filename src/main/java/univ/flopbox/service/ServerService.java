package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.Server;

import java.util.List;

/**
 * Service de gestion des serveurs FTP enregistrés sur la plateforme FlopBox.
 *
 * <p>Délègue les appels à {@link FlopboxApi} en injectant automatiquement
 * le token JWT stocké dans le {@link TokenStore}.</p>
 */
public class ServerService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    /**
     * @param api        client HTTP FlopBox
     * @param tokenStore stockage du token JWT de session
     */
    public ServerService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Récupère la liste des serveurs FTP enregistrés par l'utilisateur connecté.
     *
     * @return la liste des serveurs (alias, host, port)
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    public List<Server> getServers() {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant getServers().");
        }
        return api.getServers(tokenStore.get());
    }
}