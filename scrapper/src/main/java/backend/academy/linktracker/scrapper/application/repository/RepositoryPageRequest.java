package backend.academy.linktracker.scrapper.application.repository;

/**
 * Repository-level page request abstraction independent from specific storage APIs.
 *
 * @param limit max row count for bounded request, zero means unbounded
 * @param offset zero-based offset
 */
public record RepositoryPageRequest(int limit, long offset) {

    private static final RepositoryPageRequest ALL = new RepositoryPageRequest(0, 0);

    /**
     * Canonical constructor with non-negative guards.
     *
     * @param limit max row count for bounded request, zero means unbounded
     * @param offset zero-based offset
     */
    public RepositoryPageRequest {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
    }

    /**
     * Unbounded compatibility page request.
     *
     * @return unbounded request
     */
    public static RepositoryPageRequest all() {
        return ALL;
    }

    /**
     * Indicates whether limit should be applied.
     *
     * @return true when request is bounded
     */
    public boolean bounded() {
        return limit > 0;
    }
}
