package univ.flopbox.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.api.FlopboxApiClient;
import univ.flopbox.authService.AuthService;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.config.Log;
import univ.flopbox.config.ServerCredentials;
import univ.flopbox.config.ServerCredentialsConfig;
import univ.flopbox.model.FtpItem;
import univ.flopbox.service.DirectoryService;
import univ.flopbox.service.SyncService;

import java.io.Console;
import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public void start() {
        // Initialisation des logs
        Log.configureLogging();
        log.info("Démarrage de l'application FlopBox Client...");

        // Initialisation des services de base
        FlopboxApi api = new FlopboxApiClient();
        TokenStore tokenStore = new TokenStore();
        AuthService auth = new AuthService(api, tokenStore);
        DirectoryService directoryService = new DirectoryService(api, tokenStore);
        SyncService syncService = new SyncService(api, tokenStore);

        // 3. Authentification globale
        String email = null;
        String password = null;

        Console console = System.console();

        if (console != null) {
            // Vrai terminal : Le mot de passe sera masqué pendant la frappe
            email = console.readLine("Veuillez saisir votre email FlopBox : ");
            char[] passwordChars = console.readPassword("Veuillez saisir votre mot de passe : ");
            password = new String(passwordChars);
        } else {
            // Mode "Fallback" : Utile si vous lancez le programme depuis l'IDE (IntelliJ, Eclipse...)
            // car les IDE n'ont souvent pas de "vraie" console attachée.
            Scanner scanner = new Scanner(System.in);
            System.out.print("Veuillez saisir votre email FlopBox : ");
            email = scanner.nextLine();
            System.out.print("Veuillez saisir votre mot de passe : ");
            password = scanner.nextLine();
        }

        if (!auth.login(email, password)) {
            log.error("Connexion à la plateforme FlopBox échouée. Vérifiez vos identifiants. Arrêt de l'application.");
            return;
        }
        log.info("Connexion réussie. Token sécurisé stocké.");

        // Lecture du fichier de configuration (multi-serveurs)
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File("config.json");
        ServerCredentialsConfig config;

        try {
            if (!configFile.exists()) {
                log.error("Fichier config.json introuvable à la racine du projet ! Créez-le avant de lancer l'application.");
                return;
            }
            // Transforme le JSON directement en votre Record ServerCredentialsConfig
            config = mapper.readValue(configFile, ServerCredentialsConfig.class);
        } catch (Exception e) {
            log.error("Erreur lors de la lecture de config.json : {}", e.getMessage());
            return;
        }

        if (config.servers() == null || config.servers().isEmpty()) {
            log.warn("Aucun serveur trouvé dans la configuration.");
            return;
        }

        // Démarrage de l'agent de synchronisation pour CHAQUE serveur
        log.info("--- DÉMARRAGE DE L'AGENT DE SYNCHRONISATION MULTI-SERVEURS ---");

        // On crée autant de threads que de serveurs pour qu'ils se synchronisent en parallèle
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(config.servers().size());

        for (ServerCredentials server : config.servers()) {

            AtomicBoolean isFirstSync = new AtomicBoolean(server.alreadySynced());
            // On planifie un cycle toutes les 60 secondes pour ce serveur
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    log.info("[CYCLE] Démarrage de l'analyse sur : {}", server.host());

                    // Liste la racine avec les identifiants spécifiques au serveur
                    List<FtpItem> remoteItems = directoryService.listDirectory(
                            server.host(), "/", server.username(), server.password()
                    );

                    // Lance la synchronisation récursive
                    syncService.syncServer(server.host(), remoteItems,server.alreadySynced(), server.username(), server.password());

                    // Si c'était le premier cycle, on met à jour le fichier config.json
                    if (isFirstSync.get()) {
                        isFirstSync.set(false); // Passe à false en mémoire
                        ServerCredentialsConfig.markServerAsSynced(configFile, mapper, server.host()); // Passe à true dans le fichier
                    }

                    log.info("[CYCLE] Fin de l'analyse sur : {}", server.host());
                } catch (Exception e) {
                    log.error("[ERREUR] Cycle échoué sur {} : {}", server.host(), e.getMessage());
                }
            }, 0, 60, TimeUnit.SECONDS);
        }
    }
}
