package univ.flopbox.model;

public record Server(
        String alias,
        String host,
        String password,
        int port
) {}