package univ.flopbox.api;


import univ.flopbox.model.LoginRequest;

/**
 * Contrat d'accès à la plateforme FlopBox.
 */
public interface FlopboxApi {

    String login(LoginRequest loginRequest);
}
