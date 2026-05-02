package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service de gestion des fichiers distants via le proxy FlopBox.
 *
 * <p>Regroupe les opérations de lecture, téléchargement et envoi de fichiers.
 * Chaque méthode injecte automatiquement le token JWT du {@link TokenStore}
 * et vérifie que l'utilisateur est bien authentifié avant d'effectuer l'appel.</p>
 */
public class FileService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    /**
     * @param api        client HTTP FlopBox
     * @param tokenStore stockage du token JWT de session
     */
    public FileService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Liste le contenu d'un répertoire distant.
     *
     * @param host        l'hôte du serveur FTP cible
     * @param path        le chemin du répertoire à lister (ex: "/", "/documents")
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return la liste des éléments (fichiers et dossiers) du répertoire
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    public List<FtpItem> listDirectory(String host, String path, String ftpUser, String ftpPassword) {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant listDirectory().");
        }
        return api.listDirectory(tokenStore.get(), host, path, ftpUser, ftpPassword);
    }

    /**
     * Télécharge un fichier distant vers le système de fichiers local de manière asynchrone.
     *
     * @param host        l'hôte du serveur FTP source
     * @param remoteFile  l'élément distant à télécharger (doit être de type FILE)
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return un {@link CompletableFuture} se complétant une fois le téléchargement terminé
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    public CompletableFuture<Void> downloadFile(String host, FtpItem remoteFile, String ftpUser, String ftpPassword) {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant downloadFile().");
        }
        return api.downloadFile(tokenStore.get(), host, remoteFile, ftpUser, ftpPassword);
    }

    /**
     * Uploade un fichier local vers un serveur FTP distant de manière asynchrone.
     *
     * @param host        l'hôte du serveur FTP cible
     * @param localPath   le chemin local du fichier à envoyer
     * @param remotePath  le chemin de destination sur le serveur FTP
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return un {@link CompletableFuture} se complétant une fois l'upload terminé
     * @throws FileNotFoundException si le fichier local est introuvable
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    public CompletableFuture<Void> uploadFile(String host, String localPath, String remotePath, String ftpUser, String ftpPassword) throws FileNotFoundException {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant uploadFile().");
        }
        return api.uploadFile(tokenStore.get(), host, localPath, remotePath, ftpUser, ftpPassword);
    }
}