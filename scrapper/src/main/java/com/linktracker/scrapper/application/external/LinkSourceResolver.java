package com.linktracker.scrapper.application.external;

import com.linktracker.scrapper.application.external.link.LinkSource;
import java.util.Optional;

/**
 * Resolves tracked URL into supported external source descriptor.
 */
public interface LinkSourceResolver {

    /**
     * Resolves URL into supported source.
     *
     * @param url tracked URL
     * @return parsed source when URL is supported
     */
    Optional<LinkSource> resolve(String url);
}
