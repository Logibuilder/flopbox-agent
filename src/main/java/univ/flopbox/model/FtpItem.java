package univ.flopbox.model;

/**
 * Représente un élément (fichier ou dossier) trouvé sur un serveur FTP.
 * Contient les métadonnées essentielles renvoyées au client.
 */
public record FtpItem(
        String path,
        String name,
        Type type, // "DIRECTORY" ou "FILE"
        long size,
        String lastModified
) {

}