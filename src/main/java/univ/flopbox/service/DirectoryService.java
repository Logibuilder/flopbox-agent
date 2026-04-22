package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;

import java.util.List;

public class DirectoryService {


    private final FlopboxApi api;
    private final TokenStore tokenStore;

    public DirectoryService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Récupère la liste des serveurs FTP enregistrés dans FlopBox.
     * Nécessite d'être authentifié au préalable.
     */
    public List<FtpItem> listDirectory(String host, String path, String ftpUser, String ftpPassword) {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant getServers().");
        }
        return api.listDirectory(tokenStore.get(), host, path, ftpUser, ftpPassword );
    }
}
