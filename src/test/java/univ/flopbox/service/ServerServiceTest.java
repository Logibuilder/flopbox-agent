package univ.flopbox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.Server;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerServiceTest {
    private FlopboxApi apiMock;
    private TokenStore tokenStoreMock;
    private ServerService serverService;

    @BeforeEach
    void setUp() {
        apiMock = mock(FlopboxApi.class);
        tokenStoreMock = mock(TokenStore.class);
        serverService = new ServerService(apiMock, tokenStoreMock);
    }

    @Test
    void testGetServers_Success() {
        when(tokenStoreMock.hasToken()).thenReturn(true);
        when(tokenStoreMock.get()).thenReturn("token123");
        when(apiMock.getServers("token123")).thenReturn(List.of(new Server("alias", "host", 21)));

        List<Server> servers = serverService.getServers();

        assertFalse(servers.isEmpty());
        verify(apiMock).getServers("token123");
    }

    @Test
    void testGetServers_ThrowsExceptionIfNoToken() {
        when(tokenStoreMock.hasToken()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> serverService.getServers());
    }
}