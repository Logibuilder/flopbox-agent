package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.Server;

import java.util.List;

public class ServerService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    public ServerService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Récupère la liste des serveurs FTP enregistrés dans FlopBox.
     * Nécessite d'être authentifié au préalable.
     */
    public List<Server> getServers() {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant getServers().");
        }
        return api.getServers(tokenStore.get());
    }
}
