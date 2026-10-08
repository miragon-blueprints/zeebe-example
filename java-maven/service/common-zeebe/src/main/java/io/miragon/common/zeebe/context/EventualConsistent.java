package io.miragon.common.zeebe.context;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks ProcessEngineApi methods that provide eventual consistency guarantees.
 * Results may not reflect recent changes due to distributed system propagation delays.
 * Used to communicate consistency characteristics in distributed Zeebe environments.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface EventualConsistent {
}
