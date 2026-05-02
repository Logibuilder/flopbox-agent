package univ.flopbox.authService;

/**
 * Stocke le token JWT en mémoire pour toute la session.
 */
public class TokenStore {

    private String token;

    public void save(String token) { this.token = token; }
    public String get()            { return token; }
    public boolean hasToken()      { return !token.isEmpty() && token != null; }
}