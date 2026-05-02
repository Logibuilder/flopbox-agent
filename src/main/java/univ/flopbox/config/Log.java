package univ.flopbox.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.LoggerFactory;

/**
 * Configuration programmatique du système de logs via Logback.
 *
 * <p>À appeler une seule fois en début de {@code main} avant toute autre instruction,
 * pour garantir que tous les logs de l'application utilisent le format défini ici.</p>
 *
 * <p>Format de sortie : {@code HH:mm:ss.SSS [thread] LEVEL NomClasse - message}</p>
 * <p>Exemple : {@code 14:32:01.452 [main] INFO  SyncService - Exploration du dossier : /}</p>
 */
public class Log {

    private Log() {
        // Classe utilitaire, pas d'instanciation
    }

    /**
     * Initialise et applique la configuration Logback pour toute l'application.
     *
     * <p>Cette méthode :
     * <ol>
     *   <li>Réinitialise toute configuration Logback existante (fichier {@code logback.xml} inclus).</li>
     *   <li>Crée un appender console avec un pattern lisible.</li>
     *   <li>Fixe le niveau minimum à {@link Level#INFO} (les messages DEBUG et TRACE sont ignorés).</li>
     * </ol>
     */
    public static void configureLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        // Encoder : définit le format de chaque ligne de log
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        // Appender : envoie les logs vers la console
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("STDOUT");
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        // Application au logger racine : couvre tous les loggers de l'application
        Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(consoleAppender);
    }
}