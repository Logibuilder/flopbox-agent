package univ.flopbox;

import univ.flopbox.api.FlopboxApi;
import univ.flopbox.api.FlopboxApiClient;
import univ.flopbox.auth.AuthService;
import univ.flopbox.auth.TokenStore;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        FlopboxApi api        = new FlopboxApiClient();
        TokenStore tokenStore = new TokenStore();
        AuthService auth      = new AuthService(api, tokenStore);

        auth.login("assane.kane@gmail.com", "ass");

// Vérification
        System.out.println("Token stocké : " + tokenStore.get().substring(0, 20) + "...");
    }
}