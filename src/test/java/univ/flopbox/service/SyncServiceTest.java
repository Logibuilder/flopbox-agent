package univ.flopbox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class SyncServiceTest {
    private FlopboxApi apiMock;
    private TokenStore tokenStoreMock;
    private SyncService syncService;

    @BeforeEach
    void setUp() {
        apiMock = mock(FlopboxApi.class);
        tokenStoreMock = mock(TokenStore.class);
        syncService = new SyncService(apiMock, tokenStoreMock);

        when(tokenStoreMock.get()).thenReturn("token123");
    }

    @Test
    void testSyncServer_CreatesDeletedFolderIfNotAlreadySynced() {
        List<FtpItem> remoteItems = new ArrayList<>();

        // Simule la création du dossier distant
        when(apiMock.createRemoteDirectory(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // On appelle syncServer avec alreadySynced = false
        syncService.syncServer("localhost", remoteItems, false, "user", "pass");

        // On vérifie que la requête de création de "/.deleted" a bien été envoyée
        verify(apiMock, times(1)).createRemoteDirectory("token123", "localhost", "/.deleted", "user", "pass");
    }

    @Test
    void testSyncServer_DoesNotCreateDeletedFolderIfAlreadySynced() {
        List<FtpItem> remoteItems = new ArrayList<>();

        // On appelle syncServer avec alreadySynced = true
        syncService.syncServer("localhost", remoteItems, true, "user", "pass");

        // On vérifie que la requête n'a PAS été envoyée
        verify(apiMock, never()).createRemoteDirectory(anyString(), anyString(), eq("/.deleted"), anyString(), anyString());
    }
}