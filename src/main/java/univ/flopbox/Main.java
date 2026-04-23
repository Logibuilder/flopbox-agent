package univ.flopbox;

import univ.flopbox.model.FtpItem;
import univ.flopbox.service.DirectoryService;
import univ.flopbox.service.ServerService;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.api.FlopboxApiClient;
import univ.flopbox.authService.AuthService;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.Server;
import univ.flopbox.service.SyncService;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        FlopboxApi api        = new FlopboxApiClient();
        TokenStore tokenStore = new TokenStore();
        AuthService auth      = new AuthService(api, tokenStore);
        ServerService serverService = new ServerService(api, tokenStore);
        DirectoryService directoryService = new DirectoryService(api, tokenStore);
        auth.login("assane.kane@gmail.com", "ass");


        System.out.println("Token stocké : " + tokenStore.get().substring(0, 20) + "...");
        List<Server> list = serverService.getServers();
        list.forEach(System.out::println);

        String ftpHost = "localhost"; // ou l'alias configuré
        String ftpPath = "";
        String ftpUser = "anonymous";
        String ftpPass = "anonymous";
        List<FtpItem> list1 = directoryService.listDirectory(ftpHost, ftpPath, ftpUser, ftpPass);
        list1.forEach(System.out::println);
        System.out.println(SyncService.createDirectory(list.getFirst().host(), list1.getFirst()));


        CopyOnWriteArrayList<CompletableFuture<Void>> downloads = new CopyOnWriteArrayList<>();

        // Boucle de traitement de l'arborescence récupérée
        for (FtpItem item : list1) {
            // Création de l'arborescence (Dossier ou parents d'un fichier)
            SyncService.createDirectory(ftpHost, item);

            // Si c'est un fichier, on lance le téléchargement
            if (item.type() == univ.flopbox.model.Type.FILE) {
                CompletableFuture<Void> ddl = (api).downloadFile(
                        tokenStore.get(),
                        ftpHost,
                        item,
                        ftpUser,
                        ftpPass
                );
                downloads.add(ddl);
            }
        }

        if (!downloads.isEmpty()) {
            System.out.println("Téléchargements en cours...");
            CompletableFuture.allOf(downloads.toArray(new CompletableFuture[0])).join();
            System.out.println("Tous les fichiers ont été téléchargés avec succès.");
        }
    }
}