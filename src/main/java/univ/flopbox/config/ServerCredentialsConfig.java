package univ.flopbox.config;

import java.util.List;

public record ServerCredentialsConfig(
        List<ServerCredentials> servers
) {
}
