package univ.flopbox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import univ.flopbox.api.FlopboxApi;
import univ.flopbox.authService.TokenStore;
import univ.flopbox.model.FtpItem;
import univ.flopbox.model.Type;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileServiceTest {
    private FlopboxApi apiMock;
    private TokenStore tokenStoreMock;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        apiMock = mock(FlopboxApi.class);
        tokenStoreMock = mock(TokenStore.class);
        fileService = new FileService(apiMock, tokenStoreMock);
    }

    @Test
    void testDownloadFile_Success() {
        when(tokenStoreMock.hasToken()).thenReturn(true);
        when(tokenStoreMock.get()).thenReturn("token123");
        FtpItem item = new FtpItem("/file.txt", "file.txt", Type.FILE, 100, "date");
        when(apiMock.downloadFile("token123", "host", item, "user", "pass"))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> fileService.downloadFile("host", item, "user", "pass"));
        verify(apiMock).downloadFile("token123", "host", item, "user", "pass");
    }

    @Test
    void testUploadFile_ThrowsExceptionIfNoToken() {
        when(tokenStoreMock.hasToken()).thenReturn(false);
        assertThrows(IllegalStateException.class, () ->
                fileService.uploadFile("host", "localPath", "remotePath", "user", "pass")
        );
    }
}