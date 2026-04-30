package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.ApiResponse;
import univ.flopbox.model.FtpItem;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FileService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    public FileService(FlopboxApi api, TokenStore tokenStore) {
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
    public CompletableFuture<Void> downloadFile(String host, FtpItem remoteFile, String ftpUser, String ftpPassword) {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant getServers().");
        }

        return api.downloadFile(tokenStore.get(), host, remoteFile,ftpUser, ftpPassword);
    }


    public CompletableFuture<Void> uploadFile(String host, String localPath, String remotePath, String ftpUser, String ftpPassword) throws FileNotFoundException {
        if (!tokenStore.hasToken()) {
            throw new IllegalStateException("Non authentifié : appelez login() avant getServers().");
        }

        return api.uploadFile(tokenStore.get(), host, localPath, remotePath, ftpUser, ftpPassword);
    }
}
