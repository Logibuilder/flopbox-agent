package univ.flopbox.service;

import univ.flopbox.model.FtpItem;
import univ.flopbox.model.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SyncService {

    private static final  String ROOT_SYNC_DIR = "flopbox_data";

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
}
