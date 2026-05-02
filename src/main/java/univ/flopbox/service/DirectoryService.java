package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;

import java.util.List;

/**
 * Service de navigation dans les répertoires distants via le proxy FlopBox.
 *
 * <p>Délègue les appels à {@link FlopboxApi} en injectant automatiquement
 * le token JWT stocké dans le {@link TokenStore}.</p>
 */
public class DirectoryService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    /**
     * @param api        client HTTP FlopBox
     * @param tokenStore stockage du token JWT de session
     */
    public DirectoryService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Liste le contenu d'un répertoire distant sur un serveur FTP via le proxy FlopBox.
     *
     * @param host        l'hôte du serveur FTP cible (ex: "localhost")
     * @param path        le chemin du répertoire à lister (ex: "/", "/documents")
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return la liste des éléments (fichiers et dossiers) du répertoire
     * @throws IllegalStateException si aucun token JWT n'est disponible (utilisateur non connecté)
     */
    public List<FtpItem> listDirectory(String host, String path, String ftpUser, String ftpPassword) {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant listDirectory().");
        }
        return api.listDirectory(tokenStore.get(), host, path, ftpUser, ftpPassword);
    }
}