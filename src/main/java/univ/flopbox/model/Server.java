package univ.flopbox.model;

public record Server(
        String alias,
        String host,
        int port
) {}