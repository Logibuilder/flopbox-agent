package univ.flopbox.service;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.config.ServerCredentials;
import univ.flopbox.config.ServerCredentialsConfig;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.RenameRequest;
import univ.flopbox.model.Type;

import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Service gérant l'interface console interactive permettant à l'utilisateur
 * de manipuler la corbeille (.deleted) pendant que la synchronisation tourne en fond.
 */
public class CorbeilService {

    private final FlopboxApi api;
    private final TokenStore tokenStore;
    private final DirectoryService directoryService;
    private final ServerCredentialsConfig config;
    private final ScheduledExecutorService scheduler;

    public CorbeilService(FlopboxApi api, TokenStore tokenStore,
                          DirectoryService directoryService,
                          ServerCredentialsConfig config,
                          ScheduledExecutorService scheduler) {
        this.api = api;
        this.tokenStore = tokenStore;
        this.directoryService = directoryService;
        this.config = config;
        this.scheduler = scheduler;
    }

    public void start() {
        System.out.println("\n=======================================================");
        System.out.println("  MODE INTERACTIF : GESTION DE LA CORBEILLE (.deleted)");
        System.out.println("  Tapez 'help' pour voir les commandes disponibles.");
        System.out.println("=======================================================\n");

        Scanner cmdScanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.print("flopbox> ");
            String line = cmdScanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            switch (cmd) {
                case "help":
                    printHelp();
                    break;
                case "list-trash":
                    handleListTrash();
                    break;
                case "recover":
                    handleRecover(parts);
                    break;
                case "empty-trash":
                    handleEmptyTrash(parts);
                    break;
                case "quit":
                case "exit":
                    running = false;
                    System.out.println("Arrêt du planificateur de synchronisation...");
                    scheduler.shutdownNow();
                    System.out.println("Application terminée proprement.");
                    break;
                default:
                    System.out.println("Commande '" + cmd + "' non reconnue. Tapez 'help'.");
            }
        }
    }

    private void printHelp() {
        System.out.println("\n--- Commandes Disponibles ---");
        System.out.println("  list-trash                     : Affiche l'arborescence de la corbeille des serveurs");
        System.out.println("  recover <host> <nom_fichier>   : Restaure un élément de la corbeille vers la racine");
        System.out.println("  empty-trash <host>             : Supprime définitivement le contenu de la corbeille");
        System.out.println("  quit                           : Arrête l'agent de synchronisation et quitte");
        System.out.println();
    }

    private void handleListTrash() {
        for (ServerCredentials s : config.servers()) {
            System.out.println("\n[" + s.host() + "] /.deleted/");
            try {
                List<FtpItem> trashItems = directoryService.listDirectory(s.host(), "/.deleted", s.username(), s.password());
                if (trashItems == null || trashItems.isEmpty()) {
                    System.out.println(" └── (Corbeille vide)");
                } else {
                    for (int i = 0; i < trashItems.size(); i++) {
                        FtpItem item = trashItems.get(i);
                        String prefix = (i == trashItems.size() - 1) ? " └── " : " ├── ";
                        System.out.println(prefix + item.name() + " (" + item.type() + ", " + item.size() + " octets)");
                    }
                }
            } catch (Exception e) {
                System.out.println(" └── (Corbeille inexistante ou inaccessible)");
            }
        }
        System.out.println();
    }

    private void handleRecover(String[] parts) {
        if (parts.length < 3) {
            System.out.println("Erreur de syntaxe. Usage : recover <host> <nom_fichier>");
            return;
        }

        String host = parts[1];
        String fileName = parts[2];
        ServerCredentials server = config.servers().stream().filter(s -> s.host().equals(host)).findFirst().orElse(null);

        if (server != null) {
            try {
                // 1. Chercher l'élément dans la corbeille pour savoir si c'est un dossier ou un fichier
                List<FtpItem> trashItems = directoryService.listDirectory(host, "/.deleted", server.username(), server.password());
                FtpItem itemToRecover = trashItems.stream().filter(i -> i.name().equals(fileName)).findFirst().orElse(null);

                if (itemToRecover == null) {
                    System.out.println("Erreur : '" + fileName + "' introuvable dans la corbeille du serveur " + host);
                    return;
                }

                // 2. Renommer sur le serveur FTP
                String oldPath = "/.deleted/" + fileName;
                String newPath = "/" + fileName; // On le restaure à la racine par défaut
                api.renameFile(tokenStore.get(), host, new RenameRequest(oldPath, newPath), server.username(), server.password()).join();
                System.out.println("Succès : '" + fileName + "' a été restauré à la racine de " + host);

                // 3. FIX: Créer le fichier "fantôme" localement
                File localGhost = new File("flopbox_data/" + host + "/" + fileName);
                localGhost.getParentFile().mkdirs();

                if (itemToRecover.type() == Type.DIRECTORY) {
                    localGhost.mkdirs();
                } else {
                    localGhost.createNewFile();
                }

                // On truque la date à 1970. Au prochain cycle, l'agent verra que le serveur est plus récent et téléchargera !
                localGhost.setLastModified(0L);

            } catch (Exception e) {
                System.out.println("Erreur lors de la restauration : " + e.getMessage());
            }
        } else {
            System.out.println("Erreur : Serveur '" + host + "' introuvable dans config.json.");
        }
    }

    private void handleEmptyTrash(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Erreur de syntaxe. Usage : empty-trash <host>");
            return;
        }

        String host = parts[1];
        ServerCredentials server = config.servers().stream().filter(s -> s.host().equals(host)).findFirst().orElse(null);

        if (server != null) {
            try {
                List<FtpItem> trashItems = directoryService.listDirectory(server.host(), "/.deleted", server.username(), server.password());
                int count = 0;
                for (FtpItem item : trashItems) {
                    api.deleteFile(tokenStore.get(), host, item.path(), server.username(), server.password()).join();
                    count++;
                }
                System.out.println("Succès : " + count + " élément(s) supprimé(s) définitivement du serveur " + host);
            } catch (Exception e) {
                System.out.println("Erreur lors du vidage de la corbeille : " + e.getMessage());
            }
        } else {
            System.out.println("Erreur : Serveur '" + host + "' introuvable dans config.json.");
        }
    }
}