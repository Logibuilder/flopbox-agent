package univ.flopbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service de synchronisation entre le système de fichiers local
 * et un serveur FTP distant via le proxy FlopBox.
 *
 * <p>La synchronisation est récursive : elle descend dans tous les
 * sous-dossiers du serveur et compare les dates de modification
 * pour décider si un fichier doit être téléchargé ou uploadé.</p>
 */
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final String ROOT_SYNC_DIR = "flopbox_data";
    private static final DateTimeFormatter FTP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);

    private final FlopboxApi api;
    private final TokenStore tokenStore;

    public SyncService(FlopboxApi api, TokenStore tokenStore) {
        this.api = api;
        this.tokenStore = tokenStore;
    }

    /**
     * Supprime le slash initial d'un chemin FTP pour le rendre relatif.
     * Exemple : "/foo/bar.txt" → "foo/bar.txt"
     */
    private static String cleanPath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * Déduit le chemin du dossier courant à partir de la liste des éléments
     * qu'il contient, en prenant le parent du premier élément.
     */
    private static String deduceCurrentPath(List<FtpItem> items) {
        String firstPath = items.get(0).path();
        String parent = firstPath.contains("/")
                ? firstPath.substring(0, firstPath.lastIndexOf("/"))
                : "/";
        return parent.isEmpty() ? "/" : parent;
    }


    /**
     * Crée localement l'arborescence correspondant à un élément distant.
     * Si l'élément est un dossier, crée le dossier complet.
     * Si c'est un fichier, crée uniquement ses dossiers parents.
     *
     * @param host le nom d'hôte du serveur FTP (utilisé comme nom de dossier racine local)
     * @param item l'élément distant à reproduire localement
     * @return le chemin local correspondant à cet élément
     */
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

    /**
     * Synchronise le contenu d'un seul dossier (non récursif) :
     * <ul>
     *   <li>Télécharge les fichiers distants absents localement.</li>
     *   <li>Compare les dates des fichiers présents des deux côtés.</li>
     *   <li>Uploade les fichiers locaux absents côté distant.</li>
     * </ul>
     *
     * @param host              hôte FTP cible
     * @param currentRemotePath chemin du dossier distant en cours de traitement
     * @param remoteItems       contenu du dossier distant
     */
    public void syncMiroir(String host, String currentRemotePath, List<FtpItem> remoteItems,Boolean alreadySynced, String ftpUser, String ftpPassword) {
        Path localServerBase = Paths.get(ROOT_SYNC_DIR, host);
        Path localCurrentDir = cleanPath(currentRemotePath).isEmpty()
                ? localServerBase
                : localServerBase.resolve(cleanPath(currentRemotePath));

        // --- Téléchargement des fichiers distants ---
        for (FtpItem remoteItem : remoteItems) {
            if (remoteItem.type() != Type.FILE) continue;

            Path localFile = localServerBase.resolve(cleanPath(remoteItem.path()));
            if (!Files.exists(localFile)) {
                if (alreadySynced) {
                    // Le serveur était déjà synchronisé → fichier supprimé localement
                    log.info("Fichier supprimé localement, archivage vers .deleted/ : {}", remoteItem.name());
                    moveToDeleted(host, remoteItem, ftpUser, ftpPassword);
                } else {
                    // Première synchronisation → téléchargement initial
                    log.info("Nouveau fichier distant à télécharger : {}", remoteItem.name());
                    api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword);
                }
            } else {
                compareAndSync(host, localFile, remoteItem, ftpUser, ftpPassword);
            }
        }

        // --- Upload des fichiers locaux absents côté distant ---
        if (!Files.exists(localCurrentDir)) return;

        try (var stream = Files.list(localCurrentDir)) {
            stream.filter(Files::isRegularFile).forEach(localFile -> {
                String fileName = localFile.getFileName().toString();
                boolean existsRemote = remoteItems.stream().anyMatch(ri -> ri.name().equals(fileName));

                if (!existsRemote) {
                    String remoteFilePath = (currentRemotePath.endsWith("/")
                            ? currentRemotePath : currentRemotePath + "/") + fileName;
                    log.info("Nouveau fichier local à uploader : {}", fileName);
                    try {
                        api.uploadFile(tokenStore.get(), host, localFile.toString(), remoteFilePath, ftpUser, ftpPassword);
                    } catch (Exception e) {
                        log.error("Upload échoué pour {} : {}", fileName, e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            log.warn("Dossier local inaccessible : {}", localCurrentDir);
        }
    }

    /**
     * Compare les dates de modification locale et distante d'un fichier
     * et décide de l'action à mener (upload ou download).
     * Une tolérance de 2 secondes évite les faux positifs liés aux décalages d'horloge.
     */
    private void compareAndSync(String host, Path localFile, FtpItem remoteItem, String ftpUser, String ftpPassword) {
        try {
            long localTime  = Files.getLastModifiedTime(localFile).toMillis();
            long remoteTime = ZonedDateTime.parse(remoteItem.lastModified(), FTP_DATE_FORMAT)
                    .toInstant().toEpochMilli();

            if (localTime > remoteTime + 2000) {
                log.info("Local plus récent, upload : {}", remoteItem.name());
                api.uploadFile(tokenStore.get(), host, localFile.toString(), remoteItem.path(), ftpUser, ftpPassword);
            } else if (remoteTime > localTime + 2000) {
                log.info("Distant plus récent, download : {}", remoteItem.name());
                api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword);
            }
        } catch (Exception e) {
            log.warn("Synchronisation échouée pour {} : {}", remoteItem.name(), e.getMessage());
        }
    }

    /**
     * Synchronise récursivement l'intégralité de l'arborescence d'un serveur FTP.
     * Pour chaque dossier rencontré, appelle {@link #syncMiroir} puis descend
     * dans les sous-dossiers.
     *
     * @param host        hôte FTP cible
     * @param remoteItems contenu du dossier courant à synchroniser
     */
    public void syncServer(String host, List<FtpItem> remoteItems, String ftpUser, String ftpPassword) {
        if (remoteItems == null || remoteItems.isEmpty()) return;

        syncMiroir(host, deduceCurrentPath(remoteItems), remoteItems,true, ftpUser, ftpPassword);

        List<String> ignoredFolders = List.of(
                "server_ftp_env", // Environnement Python
                "__pycache__",    // Cache Python
                ".deleted",       // Le dossier d'archivage des suppressions !
                "target",         // Dossier de build Java
                ".git",           // Historique Git
                "node_modules"    // Dépendances Javascript (au cas où)
        );
        for (FtpItem item : remoteItems) {
            if (item.type() == Type.DIRECTORY) {

                if (ignoredFolders.contains(item.name())) {
                    log.debug("Dossier lourd/inutile ignoré : {}", item.name());
                    continue;
                }
                try {
                    log.info("Exploration du dossier : {}", item.path());
                    createDirectory(host, item);
                    List<FtpItem> subItems = api.listDirectory(tokenStore.get(), host, item.path(), ftpUser, ftpPassword);
                    syncServer(host, subItems, ftpUser, ftpPassword);
                } catch (Exception e) {
                    log.warn("Dossier non accessible ou vide (ignoré) : {}", item.path());
                }
            }
        }
    }

    /**
     * Déplace un fichier vers {@code /.deleted/} sur le serveur FTP distant.
     *
     * <p>Comme l'API ne dispose pas de commande de déplacement direct, la méthode :
     * <ol>
     *   <li>Télécharge le fichier distant dans un fichier temporaire local.</li>
     *   <li>L'uploade vers {@code /.deleted/nomDuFichier} sur le serveur.</li>
     *   <li>Supprime le fichier temporaire.</li>
     * </ol>
     *
     * @param host       hôte FTP cible
     * @param remoteItem fichier distant à archiver dans {@code /.deleted/}
     * @param ftpUser    nom d'utilisateur FTP
     * @param ftpPassword mot de passe FTP
     */
    private void moveToDeleted(String host, FtpItem remoteItem, String ftpUser, String ftpPassword)  {
        String deletedRemotePath = "/.deleted/" + remoteItem.name();
        Path tempFile = null;

        try {
            // fichier temporaire pour void si c'est à supprimer
            tempFile = Files.createTempFile("flopbox_deleted_", "_" + remoteItem.name());

            // Télécharger le fichier distant vers le temp
            // On crée un FtpItem pointant vers le temp localement
            api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword).join();

            // Uploader le fichier temporaire vers /.deleted/ sur le serveur
            api.uploadFile(tokenStore.get(), host, tempFile.toString(), deletedRemotePath, ftpUser, ftpPassword).join();

            log.info("Fichier archivé dans .deleted/ sur le serveur : {}", remoteItem.name());

        } catch (Exception e ) {
            log.error("Déplacement vers .deleted/ échoué pour {} : {}", remoteItem.name(), e.getMessage());
        } finally {
            // Nettoyer le fichier temporaire dans tous les cas
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Suppression fichier temporaire échouée : {}", e.getMessage());
                }
            }
        }
    }
}