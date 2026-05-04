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
                    Path renamedFile = findRenamedLocalFile(localCurrentDir, remoteItem, remoteItems);
                    if (renamedFile != null) {
                        String newName = renamedFile.getFileName().toString();
                        String newRemotePath = (remoteItem.path().contains("/")
                                ? remoteItem.path().substring(0, remoteItem.path().lastIndexOf("/") + 1)
                                : "/") + newName;
                        log.info("Renommage détecté : {} → {}", remoteItem.name(), newName);
                        api.renameFile(tokenStore.get(), host, new RenameRequest(remoteItem.path(), newRemotePath), ftpUser, ftpPassword).join();
                    } else {
                        log.info("Fichier supprimé localement, archivage vers .deleted/ : {}", remoteItem.name());
                        moveToDeleted(host, remoteItem, ftpUser, ftpPassword);
                    }
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

        try {
            // Chemin local où downloadFile écrit le fichier
            Path localFilePath = Paths.get(ROOT_SYNC_DIR, host).resolve(cleanPath(remoteItem.path()));

            // Télécharger le fichier distant localement
            api.downloadFile(tokenStore.get(), host, remoteItem, ftpUser, ftpPassword).join();

            if (!Files.exists(localFilePath)) {
                log.error("Fichier téléchargé introuvable localement : {}", localFilePath);
                return;
            }

            // Uploader vers /.deleted/ sur le serveur
            api.uploadFile(tokenStore.get(), host, localFilePath.toString(), deletedRemotePath, ftpUser, ftpPassword).join();
            log.info("Fichier archivé dans .deleted/ : {}", remoteItem.name());

            // Supprimer le fichier à son emplacement d'origine sur le serveur
            api.deleteFile(tokenStore.get(), host, remoteItem.path(), ftpUser, ftpPassword).join();
            log.info("Fichier original supprimé du serveur : {}", remoteItem.path());

        } catch (Exception e) {
            log.error("Déplacement vers .deleted/ échoué pour {} : {}", remoteItem.name(), e.getMessage());
        }
    }


    /**
     * Cherche si un fichier a été renommé localement.
     * Version avec des boucles simples (sans Streams).
     */
    private Path findRenamedLocalFile(Path localCurrentDir, FtpItem remoteItem, List<FtpItem> remoteItems) {
        if (!Files.exists(localCurrentDir)) {
            return null; // Le dossier local n'existe même pas
        }

        // On récupère la date du fichier distant (en millisecondes)
        long remoteTime;
        try {
            remoteTime = ZonedDateTime.parse(remoteItem.lastModified(), FTP_DATE_FORMAT)
                    .toInstant().toEpochMilli();
        } catch (Exception e) {
            log.warn("Impossible de lire la date de {}", remoteItem.name());
            return null;
        }

        // On récupère TOUS les fichiers du dossier local dans une liste
        List<Path> localFiles;
        try (var stream = Files.list(localCurrentDir)) {
            localFiles = stream.toList();
        } catch (IOException e) {
            log.warn("Impossible de lister le dossier local : {}", localCurrentDir);
            return null;
        }

        // On inspecte chaque fichier local un par un (le jeu des 4 indices)
        for (Path localFile : localFiles) {

            try {
                // Est-ce bien un fichier (et pas un sous-dossier) ?
                if (!Files.isRegularFile(localFile)) {
                    continue; // On passe au suivant
                }

                // Est-ce qu'il a EXACTEMENT la même taille ?
                if (Files.size(localFile) != remoteItem.size()) {
                    continue; // On passe au suivant
                }

                // Est-ce qu'il a la même date de modification (à 2 secondes près) ?
                long localTime = Files.getLastModifiedTime(localFile).toMillis();
                if (Math.abs(localTime - remoteTime) > 2000) {
                    continue; // On passe au suivant
                }

                // Est-ce que ce nom existe déjà sur le serveur ?
                String localName = localFile.getFileName().toString();
                boolean existsOnServer = false;

                for (FtpItem ri : remoteItems) {
                    if (ri.name().equals(localName)) {
                        existsOnServer = true;
                        break; // On arrête de chercher sur le serveur, on l'a trouvé
                    }
                }

                if (existsOnServer) {
                    continue; // Ce n'est pas notre fichier renommé, on passe au suivant
                }

                return localFile;

            } catch (IOException e) {
                // S'il y a un problème de lecture sur ce fichier précis, on l'ignore
                log.warn("Impossible d'inspecter le fichier {}", localFile);
            }
        }

        // Si on a fini la boucle sans rien trouver, c'est que le fichier n'a pas été renommé.
        // Il a donc vraiment été supprimé.
        return null;
    }


}