package univ.flopbox.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import univ.flopbox.service.SyncService;

import java.io.File;
import java.util.List;

public record ServerCredentialsConfig(
        @JsonProperty("servers") List<ServerCredentials> servers
) {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    /**
     * Met à jour le fichier config.json pour passer alreadySynced à true pour un serveur donné.
     * Le mot-clé 'synchronized' empêche les conflits si plusieurs serveurs finissent en même temps.
     */
    public static synchronized void markServerAsSynced(File configFile, ObjectMapper mapper, String host) {
        try {
            // On relit la configuration actuelle
            ServerCredentialsConfig currentConfig = mapper.readValue(configFile, ServerCredentialsConfig.class);

            // On recrée la liste en modifiant uniquement le serveur concerné
            List<ServerCredentials> updatedServers = currentConfig.servers().stream()
                    .map(s -> {
                        if (s.host().equals(host) && !s.alreadySynced()) {
                            // On crée un nouveau Record avec alreadySynced = true
                            return new ServerCredentials(s.host(), s.username(), s.password(), true);
                        }
                        return s;
                    })
                    .toList();

            ServerCredentialsConfig newConfig = new ServerCredentialsConfig(updatedServers);

            // On sauvegarde le JSON avec une belle indentation
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, newConfig);
            log.info("Fichier config.json mis à jour de manière permanente : alreadySynced = true pour {}", host);

        } catch (Exception e) {
            log.error("Erreur lors de l'écriture dans config.json : {}", e.getMessage());
        }
    }
}
