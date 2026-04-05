package univ.flopbox.model;

public record LoginRequest(
        String mail,
        String password
) {
}
