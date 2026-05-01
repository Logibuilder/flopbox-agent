package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class SyncService {

    private static final  String ROOT_SYNC_DIR = "flopbox_data";

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    public SyncService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Crée un dossier localement en respectant l'arborescence du serveur.
     * Gère un nombre infini de sous-répertoires.
     * * @param host L'hôte du serveur (ex: ftp.ubuntu.com)
     * @param item le fichier ou le dossier distant
     * @return Le chemin (Path) vers le dossier créé
     */
    public static Path createDirectory(String host, FtpItem item) {
        Path serverBaseDir = Paths.get(ROOT_SYNC_DIR, host);
        String cleanPath = item.path().startsWith("/") ? item.path().substring(1) : item.path();
        Path localPath = serverBaseDir.resolve(cleanPath);

        try {
            if (item.type() == Type.DIRECTORY) {
                // Si c'est un dossier, on crée toute l'arborescence
                Files.createDirectories(localPath);
                System.out.println("[DIR] Dossier créé : " + localPath);
            } else {
                // Si c'est un fichier, on crée uniquement les dossiers parents
                Path parentDir = localPath.getParent();
                if (parentDir != null) {
                    Files.createDirectories(parentDir);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur système de fichiers pour : " + item.path(), e);
        }

        return localPath;
    }

    public void syncMiroir(String host, List<FtpItem> remoteItems, String ftpUser, String ftpPassword) {
        Path localServerBase = Paths.get(ROOT_SYNC_DIR, host);
        String firstPath = remoteItems.get(0).path();
        String remoteDirPath = firstPath.contains("/") ? firstPath.substring(0, firstPath.lastIndexOf("/")) : "/";
        for (FtpItem remoteItem : remoteItems) {
            if (remoteItem.type() == Type.FILE) {
                String cleanPath = remoteItem.path().startsWith("/") ? remoteItem.path().substring(1) : remoteItem.path();
                Path localFile = localServerBase.resolve(cleanPath);

                if (!Files.exists(localFile)) {
                    System.out.println("[SYNC] Nouveau fichier distant : ");
                    api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword);
                    // déplacer le fichier dans le dossier deleted au niveau du serveur, car à l'utilisation de cette fonction, on suppose que le serveur est déja puller
                } else {
                    compareAndSync(host,localFile, remoteItem, ftpUser, ftpPassword);
                }
            }
        }

        try (var stream = Files.list(localServerBase)) {
            List<Path> localFiles = stream.filter(Files::isRegularFile).toList();

            for (Path localFile : localFiles) {
                String fileName = localFile.getFileName().toString();
                boolean existsRemote = remoteItems.stream().anyMatch(ri -> ri.name().equals(fileName));

                if (!existsRemote) {
                    // On utilise le path du dossier déduit plus haut pour l'upload
                    String remoteFilePath = (remoteDirPath.endsWith("/") ? remoteDirPath : remoteDirPath + "/") + fileName;
                    System.out.println("[SYNC] Nouveau fichier local détecté : " + fileName);
                    api.uploadFile(tokenStore.get(), host,localFile.toString(), remoteFilePath, ftpUser, ftpPassword);
                }
            }
        } catch (IOException e) {
            // Dossier peut ne pas exister au premier pull
        }
    }

    private void compareAndSync(String host, Path localFile, FtpItem remoteItem, String ftpUser, String ftpPassword) {
        try {
            long localTime = Files.getLastModifiedTime(localFile).toMillis();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);

            long remoteTime = ZonedDateTime.parse(remoteItem.lastModified(), formatter)
                    .toInstant()
                    .toEpochMilli();

            if (localTime > remoteTime + 2000) {
                System.out.println("[SYNC] Version locale plus récente -> Upload : " + remoteItem.name());
                api.uploadFile(tokenStore.get(), host,localFile.toString(), remoteItem.path(), ftpUser, ftpPassword);
            } else if (remoteTime > localTime + 2000) {
                System.out.println("[SYNC] Version distante plus récente -> Download : " + remoteItem.name());
                api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword);
            }
        } catch (Exception e) {
            System.out.println("Synchronisation de " + remoteItem.name() + " échouée : " + e.getMessage());
        }
    }

    public void syncServer(String host, List<FtpItem> remoteItems, String ftpUser, String ftpPassword) {
        if (remoteItems == null || remoteItems.isEmpty()) return;

        // Synchroniser les fichiers du dossier courant
        syncMiroir(host, remoteItems, ftpUser, ftpPassword);

        // Explorer les sous-dossiers
        for (FtpItem item : remoteItems) {
            if (item.type() == Type.DIRECTORY) {
                try {
                    System.out.println("[DIR] Exploration de : " + item.path());
                    // Créer le dossier local avant de lister
                    createDirectory(host, item);

                    // Lister le sous-dossier
                    List<FtpItem> subItems = api.listDirectory(tokenStore.get(), host, item.path(), ftpUser, ftpPassword);
                    syncServer(host, subItems, ftpUser, ftpPassword);
                } catch (Exception e) {
                    System.out.println("[INFO] Dossier non accessible ou vide (ignoré) : " + item.path());
                }
            }
        }
    }

}
