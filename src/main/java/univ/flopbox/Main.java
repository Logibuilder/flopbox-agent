package univ.flopbox;

import univ.flopbox.model.FtpItem;
import univ.flopbox.service.DirectoryService;
import univ.flopbox.service.ServerService;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.api.FlopboxApiClient;
import univ.flopbox.authService.AuthService;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.Server;

import java.util.List;

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

        String ftpHost = "ftp.free.fr"; // ou l'alias configuré
        String ftpPath = "/";
        String ftpUser = "anonymous";
        String ftpPass = "anonymous";
        List<FtpItem> list1 = directoryService.listDirectory(ftpHost, ftpPath, ftpUser, ftpPass);
        list1.forEach(System.out::println);

    }
}