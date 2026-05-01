package univ.flopbox;

import univ.flopbox.model.FtpItem;
import univ.flopbox.service.DirectoryService;
import univ.flopbox.service.FileService;
import univ.flopbox.service.ServerService;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.api.FlopboxApiClient;
import univ.flopbox.authService.AuthService;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.Server;
import univ.flopbox.service.SyncService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Main {
    public static void main(String[] args) {

        FlopboxApi api        = new FlopboxApiClient();
        TokenStore tokenStore = new TokenStore();
        AuthService auth      = new AuthService(api, tokenStore);

        if (!auth.login("assane.kane@gmail.com", "ass")) {
            System.out.println("Connection échouée");
            return;
        }

        ServerService serverService = new ServerService(api, tokenStore);
        DirectoryService directoryService = new DirectoryService(api, tokenStore);

        System.out.println("Token stocké : " + tokenStore.get().substring(0, 20) + "...");

        // Variables pour cibler UNIQUEMENT localhost
        String ftpHost = "localhost";
        String ftpPath = "";
        String ftpUser = "anonymous";
        String ftpPass = "anonymous";

        List<FtpItem> list1 = directoryService.listDirectory(ftpHost, ftpPath, ftpUser, ftpPass);
        list1.forEach(System.out::println);

        if(!list1.isEmpty()) {
            System.out.println(SyncService.createDirectory(ftpHost, list1.getFirst()));
        }

        // --- TEST D'UPLOAD INITIAL (optionnel, vous pouvez le commenter) ---
        FileService fileService = new FileService(api, tokenStore);
        System.out.println("Début de l'upload...");
        try {
            CompletableFuture<Void> uploadTask = fileService.uploadFile(
                    ftpHost,
                    "project_key",
                    "/",
                    ftpUser,
                    ftpPass
            );
            uploadTask.join();
            System.out.println("Fin du test d'upload.");
        } catch (Exception e) {
            System.out.println("Note : Le test d'upload a échoué : " + e.getMessage());
        }

        // --- DÉMARRAGE DE L'AGENT DE SYNCHRONISATION (Restreint à Localhost) ---
        SyncService syncService = new SyncService(api, tokenStore);

        System.out.println("\n--- DÉMARRAGE DE L'AGENT DE SYNCHRONISATION FLOPBOX ---");

        java.util.concurrent.ScheduledExecutorService scheduler =
                java.util.concurrent.Executors.newScheduledThreadPool(1);

//        scheduler.scheduleAtFixedRate(() -> {
//            try {
//                System.out.println("\n[CYCLE] Démarrage d'un nouveau cycle de synchronisation sur LOCALHOST...");
//
//                // MODIFICATION ICI : On ne boucle plus sur tous les serveurs.
//                // On utilise directement la variable ftpHost ("localhost")
//
//                // 1. On liste le dossier courant (ici la racine "/")
//                List<FtpItem> remoteItems = directoryService.listDirectory(ftpHost, "/", ftpUser, ftpPass);
//
//                // 2. On lance le miroir uniquement pour localhost
//                syncService.syncMiroir(ftpHost, remoteItems, ftpUser, ftpPass);
//
//                System.out.println("[CYCLE] Fin du cycle. Prochaine vérification dans 60 secondes.");
//
//            } catch (Exception e) {
//                System.err.println("Erreur lors du cycle : " + e.getMessage());
//            }
//        }, 0, 60, java.util.concurrent.TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("\n[CYCLE] Démarrage du cycle récursif sur : " + ftpHost);

                // 1. On récupère TOUT le contenu de la racine "/"
                List<FtpItem> remoteItems = directoryService.listDirectory(ftpHost, "/", ftpUser, ftpPass);

                // 2. On lance la synchro totale (récursive)
                syncService.syncServer(ftpHost, remoteItems, ftpUser, ftpPass);

                System.out.println("[CYCLE] Fin du cycle.");
            } catch (Exception e) {
                System.err.println("Erreur : " + e.getMessage());
            }
        }, 0, 60, java.util.concurrent.TimeUnit.SECONDS);
    }
}