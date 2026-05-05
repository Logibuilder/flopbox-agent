package univ.flopbox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectoryServiceTest {

    private FlopboxApi apiMock;
    private TokenStore tokenStoreMock;
    private DirectoryService directoryService;

    @BeforeEach
    void setUp() {
        apiMock = mock(FlopboxApi.class);
        tokenStoreMock = mock(TokenStore.class);
        directoryService = new DirectoryService(apiMock, tokenStoreMock);
    }

    @Test
    void testListDirectory_Success() {
        when(tokenStoreMock.hasToken()).thenReturn(true);
        when(tokenStoreMock.get()).thenReturn("token123");
        List<FtpItem> mockList = List.of(new FtpItem("/dir", "dir", Type.DIRECTORY, 0, "date"));
        when(apiMock.listDirectory("token123", "host", "/", "user", "pass")).thenReturn(mockList);

        List<FtpItem> result = directoryService.listDirectory("host", "/", "user", "pass");

        assertEquals(1, result.size());
        verify(apiMock).listDirectory("token123", "host", "/", "user", "pass");
    }

    @Test
    void testListDirectory_ThrowsExceptionIfNoToken() {
        when(tokenStoreMock.hasToken()).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                directoryService.listDirectory("host", "/", "user", "pass")
        );
    }
}