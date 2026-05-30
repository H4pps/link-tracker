package backend.academy.linktracker.scrapper.domain.model;

/**
 * Standalone tag projection used across application and infrastructure boundaries.
 *
 * @param id internal tag identifier
 * @param name tag name
 */
public record Tag(long id, String name) {}
