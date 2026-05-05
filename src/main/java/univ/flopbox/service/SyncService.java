package univ.flopbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.RenameRequest;
import univ.flopbox.model.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final String ROOT_SYNC_DIR = "flopbox_data";
    private static final int MAX_DEPTH = 10;
    private static final DateTimeFormatter FTP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    public SyncService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    private static String cleanPath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    public static Path createDirectory(String host, FtpItem item) {
        Path localPath = Paths.get(ROOT_SYNC_DIR, host).resolve(cleanPath(item.path()));
        try {
            if (item.type() == Type.DIRECTORY) {
                if (!Files.exists(localPath)) {
                    Files.createDirectories(localPath);
                    log.info("Dossier créé : {}", localPath);
                }
            } else {
                Path parentDir = localPath.getParent();
                if (parentDir != null) Files.createDirectories(parentDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur système de fichiers pour : " + item.path(), e);
        }
        return localPath;
    }

    public void syncMiroir(String host, String currentRemotePath, List<FtpItem> remoteItems, Boolean alreadySynced, String ftpUser, String ftpPassword) {
        Path localServerBase = Paths.get(ROOT_SYNC_DIR, host);
        Path localCurrentDir = cleanPath(currentRemotePath).isEmpty()
                ? localServerBase
                : localServerBase.resolve(cleanPath(currentRemotePath));

        Set<String> processedLocalItems = new HashSet<>();

        // 1. Analyse des éléments distants (Téléchargement ou Archivage local)
        for (FtpItem remoteItem : remoteItems) {
            Path localFile = localServerBase.resolve(cleanPath(remoteItem.path()));

            if (!Files.exists(localFile)) {
                if (alreadySynced) {
                    Path renamedFile = findRenamedLocalItem(localCurrentDir, remoteItem, remoteItems);
                    if (renamedFile != null) {
                        String newName = renamedFile.getFileName().toString();
                        String newRemotePath = (remoteItem.path().contains("/")
                                ? remoteItem.path().substring(0, remoteItem.path().lastIndexOf("/") + 1)
                                : "/") + newName;
                        log.info("Renommage détecté : {} → {}", remoteItem.name(), newName);
                        api.renameFile(tokenStore.get(), host, new RenameRequest(remoteItem.path(), newRemotePath), ftpUser, ftpPassword).join();
                        processedLocalItems.add(newName); // Évite de l'uploader en double juste après
                    } else {
                        log.info("Élément supprimé localement, archivage vers .deleted/ : {}", remoteItem.name());
                        moveToDeleted(host, remoteItem, ftpUser, ftpPassword);
                    }
                } else {
                    if (remoteItem.type() == Type.FILE) {
                        log.info("Nouveau fichier distant à télécharger : {}", remoteItem.name());
                        api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword).join();
                    }
                }
            } else {
                if (remoteItem.type() == Type.FILE) {
                    compareAndSync(host, localFile, remoteItem, ftpUser, ftpPassword);
                }
            }
        }

        if (!Files.exists(localCurrentDir)) return;

        List<String> ignoredFolders = List.of(
                "server_ftp_env", "__pycache__", ".deleted", "target", ".git", "node_modules", ".idea"
        );

        // 2. Upload des éléments locaux NOUVEAUX (Fichiers ET Dossiers)
        try (var stream = Files.list(localCurrentDir)) {
            stream.forEach(localFile -> {
                String fileName = localFile.getFileName().toString();
                if (ignoredFolders.contains(fileName) || processedLocalItems.contains(fileName)) {
                    return;
                }

                boolean existsRemote = remoteItems.stream().anyMatch(ri -> ri.name().equals(fileName));

                if (!existsRemote) {
                    String remoteFilePath = (currentRemotePath.endsWith("/")
                            ? currentRemotePath : currentRemotePath + "/") + fileName;

                    if (Files.isRegularFile(localFile)) {
                        log.info("Nouveau fichier local à uploader : {}", fileName);
                        try {
                            api.uploadFile(tokenStore.get(), host, localFile.toString(), remoteFilePath, ftpUser, ftpPassword).join();
                        } catch (Exception e) {
                            log.error("Upload échoué pour {} : {}", fileName, e.getMessage());
                        }
                    } else if (Files.isDirectory(localFile)) {
                        log.info("Nouveau dossier local détecté, création sur le serveur : {}", fileName);
                        try {
                            api.createRemoteDirectory(tokenStore.get(), host, remoteFilePath, ftpUser, ftpPassword).join();
                            // Explorer immédiatement ce nouveau dossier local pour uploader son contenu récursivement
                            syncServerBis(host, remoteFilePath, new ArrayList<>(), alreadySynced, 1, ftpUser, ftpPassword);
                        } catch (Exception e) {
                            log.error("Création distante échouée pour {} : {}", fileName, e.getMessage());
                        }
                    }
                }
            });
        } catch (IOException e) {
            log.warn("Dossier local inaccessible : {}", localCurrentDir);
        }
    }

    private void compareAndSync(String host, Path localFile, FtpItem remoteItem, String ftpUser, String ftpPassword) {
        try {
            long localTime  = Files.getLastModifiedTime(localFile).toMillis();
            long remoteTime = ZonedDateTime.parse(remoteItem.lastModified(), FTP_DATE_FORMAT)
                    .toInstant().toEpochMilli();

            if (localTime > remoteTime + 2000) {
                log.info("Local plus récent, upload : {}", remoteItem.name());
                api.uploadFile(tokenStore.get(), host, localFile.toString(), remoteItem.path(), ftpUser, ftpPassword).join();
            } else if (remoteTime > localTime + 2000) {
                log.info("Distant plus récent, download : {}", remoteItem.name());
                api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword).join();
            }
        } catch (Exception e) {
            log.warn("Synchronisation échouée pour {} : {}", remoteItem.name(), e.getMessage());
        }
    }

    public void syncServer(String host, List<FtpItem> remoteItems, Boolean alreadSynced, String ftpUser, String ftpPassword) {
        if (remoteItems == null) remoteItems = new ArrayList<>();

        try {
            if (!alreadSynced) {
                api.createRemoteDirectory(tokenStore.get(), host, "/.deleted", ftpUser, ftpPassword).join();
            }
        } catch (Exception e) {
            log.warn("Impossible de vérifier/créer le dossier /.deleted sur le serveur : {}", e.getMessage());
        }

        // On passe explicitement "/" comme chemin de départ pour ne pas dépendre de remoteItems
        syncServerBis(host, "/", remoteItems, alreadSynced, 1, ftpUser, ftpPassword);
    }

    public void syncServerBis(String host, String currentRemotePath, List<FtpItem> remoteItems, Boolean alreadySynced, int currentDepth, String ftpUser, String ftpPassword) {
        if (remoteItems == null) remoteItems = new ArrayList<>();

        // Exécute la logique dans le dossier courant
        syncMiroir(host, currentRemotePath, remoteItems, alreadySynced, ftpUser, ftpPassword);

        if (currentDepth >= MAX_DEPTH) {
            log.info("Profondeur maximale ({}) atteinte, on ignore les sous-dossiers.", MAX_DEPTH);
            return;
        }

        List<String> ignoredFolders = List.of(
                "server_ftp_env", "__pycache__", ".deleted", "target", ".git", "node_modules", ".idea"
        );

        // Explorer les sous-dossiers existants côté serveur
        for (FtpItem item : remoteItems) {
            if (item.type() == Type.DIRECTORY) {
                if (ignoredFolders.contains(item.name())) continue;

                Path localDirPath = Paths.get(ROOT_SYNC_DIR, host).resolve(cleanPath(item.path()));
                if (alreadySynced && !Files.exists(localDirPath)) {
                    continue; // Supprimé ou renommé localement, on l'ignore
                }

                try {
                    log.info("Exploration du dossier distant : {}", item.path());
                    createDirectory(host, item);
                    List<FtpItem> subItems = api.listDirectory(tokenStore.get(), host, item.path(), ftpUser, ftpPassword);
                    syncServerBis(host, item.path(), subItems, alreadySynced, currentDepth + 1, ftpUser, ftpPassword);
                } catch (Exception e) {
                    log.warn("Dossier distant non accessible (ignoré) : {}", item.path());
                }
            }
        }
    }

    private void moveToDeleted(String host, FtpItem remoteItem, String ftpUser, String ftpPassword)  {
        String deletedRemotePath = "/.deleted/" + remoteItem.name();
        try {
            api.renameFile(tokenStore.get(), host, new RenameRequest(remoteItem.path(), deletedRemotePath), ftpUser, ftpPassword).join();
            log.info("Élément archivé dans .deleted/ sur le serveur : {}", remoteItem.name());
        } catch (Exception e) {
            log.error("Déplacement vers .deleted/ échoué pour {} : {}", remoteItem.name(), e.getMessage());
        }
    }

    private Path findRenamedLocalItem(Path localCurrentDir, FtpItem remoteItem, List<FtpItem> remoteItems) {
        if (!Files.exists(localCurrentDir)) return null;

        List<Path> localItems;
        try (var stream = Files.list(localCurrentDir)) {
            localItems = stream.toList();
        } catch (IOException e) {
            return null;
        }

        List<String> ignoredFolders = List.of(
                "server_ftp_env", "__pycache__", ".deleted", "target", ".git", "node_modules", ".idea"
        );

        for (Path localItem : localItems) {
            try {
                String localName = localItem.getFileName().toString();
                if (ignoredFolders.contains(localName)) continue;

                boolean isRemoteDir = remoteItem.type() == Type.DIRECTORY;
                boolean isLocalDir = Files.isDirectory(localItem);

                if (isRemoteDir != isLocalDir) continue;

                boolean existsOnServer = remoteItems.stream().anyMatch(ri -> ri.name().equals(localName));
                if (existsOnServer) continue;

                if (!isLocalDir) {
                    if (Files.size(localItem) != remoteItem.size()) continue;
                    long remoteTime;
                    try {
                        remoteTime = ZonedDateTime.parse(remoteItem.lastModified(), FTP_DATE_FORMAT).toInstant().toEpochMilli();
                    } catch (Exception e) {
                        continue;
                    }
                    long localTime = Files.getLastModifiedTime(localItem).toMillis();
                    if (Math.abs(localTime - remoteTime) > 2000) continue;
                }

                return localItem;
            } catch (IOException e) {
                log.warn("Impossible d'inspecter l'élément {}", localItem);
            }
        }
        return null;
    }
}