package univ.flopbox.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.config.ServerCredentialsConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorbeilServiceTest {
    private FlopboxApi apiMock;
    private TokenStore tokenStoreMock;
    private DirectoryService directoryServiceMock;
    private ServerCredentialsConfig configMock;
    private ScheduledExecutorService schedulerMock;
    private CorbeilService corbeilService;

    private final InputStream systemIn = System.in;

    @BeforeEach
    void setUp() {
        apiMock = mock(FlopboxApi.class);
        tokenStoreMock = mock(TokenStore.class);
        directoryServiceMock = mock(DirectoryService.class);
        configMock = new ServerCredentialsConfig(new ArrayList<>()); // Config vide
        schedulerMock = mock(ScheduledExecutorService.class);

        corbeilService = new CorbeilService(apiMock, tokenStoreMock, directoryServiceMock, configMock, schedulerMock);
    }

    @AfterEach
    void tearDown() {
        // Restaure l'entrée standard après le test
        System.setIn(systemIn);
    }

    @Test
    void testStart_QuitCommandShutsDownScheduler() {
        // Simule un utilisateur qui tape "quit" puis Entrée
        ByteArrayInputStream in = new ByteArrayInputStream("quit\n".getBytes());
        System.setIn(in);

        // L'appel ne bloquera pas car le Scanner lira "quit" et sortira de la boucle
        corbeilService.start();

        // Vérifie que le scheduler a bien été arrêté
        verify(schedulerMock).shutdownNow();
    }

    @Test
    void testStart_UnknownCommandThenQuit() {
        // Simule un utilisateur qui tape une mauvaise commande, puis quitte
        ByteArrayInputStream in = new ByteArrayInputStream("blabla\nquit\n".getBytes());
        System.setIn(in);

        corbeilService.start();

        verify(schedulerMock).shutdownNow();
    }
}