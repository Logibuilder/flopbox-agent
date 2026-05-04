package univ.flopbox.api;

import univ.flopbox.model.FtpItem;
import univ.flopbox.model.LoginRequest;
import univ.flopbox.model.RenameRequest;
import univ.flopbox.model.Server;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Contrat d'accès à la plateforme FlopBox.
 *
 * <p>Définit les opérations disponibles pour interagir avec le proxy FlopBox :
 * authentification, gestion des serveurs FTP enregistrés, navigation dans
 * les répertoires distants, et transfert de fichiers.</p>
 */
public interface FlopboxApi {

    /**
     * Authentifie un utilisateur sur la plateforme FlopBox et retourne un token JWT.
     *
     * @param loginRequest les identifiants de connexion (email + mot de passe)
     * @return le token JWT à utiliser pour les requêtes suivantes,
     *         ou une chaîne vide en cas d'échec
     */
    String login(LoginRequest loginRequest);

    /**
     * Récupère la liste des serveurs FTP enregistrés pour l'utilisateur connecté.
     *
     * @param token le token JWT de l'utilisateur authentifié
     * @return la liste des serveurs enregistrés (alias, host, port)
     */
    List<Server> getServers(String token);

    /**
     * Liste le contenu d'un répertoire sur un serveur FTP distant via le proxy FlopBox.
     *
     * @param token       le token JWT de l'utilisateur authentifié
     * @param host        l'hôte du serveur FTP cible (ex: "localhost")
     * @param path        le chemin du répertoire à lister (ex: "/", "/documents")
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return la liste des éléments (fichiers et dossiers) contenus dans le répertoire
     */
    List<FtpItem> listDirectory(String token, String host, String path, String ftpUser, String ftpPassword);

    /**
     * Télécharge un fichier distant vers le système de fichiers local de manière asynchrone.
     * Le fichier est enregistré dans le répertoire de synchronisation local
     * en respectant l'arborescence distante.
     *
     * @param token       le token JWT de l'utilisateur authentifié
     * @param host        l'hôte du serveur FTP source
     * @param item        l'élément distant à télécharger (doit être de type FILE)
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return un {@link CompletableFuture} se complétant une fois le téléchargement terminé
     */
    CompletableFuture<Void> downloadFile(String token, String host, FtpItem item, String ftpUser, String ftpPassword);

    /**
     * Uploade un fichier local vers un serveur FTP distant de manière asynchrone.
     *
     * @param token       le token JWT de l'utilisateur authentifié
     * @param host        l'hôte du serveur FTP cible
     * @param localPath   le chemin absolu ou relatif du fichier local à envoyer
     * @param remotePath  le chemin de destination sur le serveur FTP (ex: "/documents/rapport.pdf")
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return un {@link CompletableFuture} se complétant une fois l'upload terminé
     * @throws FileNotFoundException si le fichier local spécifié par {@code localPath} n'existe pas
     */
    CompletableFuture<Void> uploadFile(String token, String host, String localPath, String remotePath, String ftpUser, String ftpPassword) throws FileNotFoundException;

    /**
     * Supprime définitivement un fichier sur le serveur FTP distant.
     *
     * @param token       le token JWT de l'utilisateur authentifié
     * @param host        l'hôte du serveur FTP cible
     * @param remotePath  le chemin du fichier à supprimer sur le serveur FTP
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return un {@link CompletableFuture} se complétant une fois la suppression effectuée
     */
    CompletableFuture<Void> deleteFile(String token, String host, String remotePath, String ftpUser, String ftpPassword);

    /**
     * Renomme un fichier sur le serveur FTP distant via le proxy FlopBox.
     *
     * @param token       le token JWT de l'utilisateur authentifié
     * @param host        l'hôte du serveur FTP cible
     * @param renameRequest     l'ancien et le nouveau chemin du fichier sur le serveur FTP
     * @param ftpUser     le nom d'utilisateur FTP
     * @param ftpPassword le mot de passe FTP
     * @return un {@link CompletableFuture} se complétant une fois le renommage effectué
     */
    CompletableFuture<Void> renameFile(String token, String host, RenameRequest renameRequest, String ftpUser, String ftpPassword);
}