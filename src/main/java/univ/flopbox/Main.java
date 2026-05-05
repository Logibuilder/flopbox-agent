package univ.flopbox;

import univ.flopbox.app.App;
import univ.flopbox.config.Log;
import univ.flopbox.model.FtpItem;
import univ.flopbox.service.DirectoryService;
import univ.flopbox.service.FileService;
import univ.flopbox.service.ServerService;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.api.FlopboxApiClient;
import univ.flopbox.authService.AuthService;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.service.SyncService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Main {
    public static void main(String[] args) {
        App flopBoxAgent = new App();

        flopBoxAgent.start();
    }
}