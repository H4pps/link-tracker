package com.linktracker.scrapper.infrastructure.resilience;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Failure classification for outbound gRPC calls.
 */
public final class GrpcResiliencePredicates {

    private static final Set<Status.Code> TRANSIENT_STATUSES = EnumSet.of(
            Status.Code.UNAVAILABLE,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.INTERNAL,
            Status.Code.RESOURCE_EXHAUSTED,
            Status.Code.UNKNOWN);

    private GrpcResiliencePredicates() {}

    public static boolean isRetryableFailure(Throwable throwable) {
        return throwable instanceof StatusRuntimeException statusException
                && TRANSIENT_STATUSES.contains(statusException.getStatus().getCode());
    }

    public static boolean isCircuitBreakerFailure(Throwable throwable) {
        return isRetryableFailure(throwable);
    }
}
