package univ.flopbox.api;


import univ.flopbox.model.LoginRequest;
import univ.flopbox.model.Server;

import java.util.List;

/**
 * Contrat d'accès à la plateforme FlopBox.
 */
public interface FlopboxApi {

    String login(LoginRequest loginRequest);
    List<Server> getServers(String token);
}
