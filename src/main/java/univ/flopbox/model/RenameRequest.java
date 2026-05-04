package univ.flopbox.model;


/**
 * DTO représentant la requête du client pour renommer un fichier ou un dossier sur le FTP.
 */
public record RenameRequest(
        String oldName,
        String newName
) {
}
