package io.miragon.common.test.assertions;

import io.miragon.bpmn.runtime.path.PathWalk;

/**
 * Bridges bpmn-to-code's compile-checked {@link PathWalk} to Camunda's {@code ProcessInstanceAssert}, whose
 * element assertions take plain element ids:
 *
 * <pre>{@code
 * assertThatProcessInstance(instance).hasCompletedElementsInOrder(ProcessPathIds.inOrder(path));
 * }</pre>
 *
 * {@link #inOrder} keeps the walk order including repeats (for {@code hasCompletedElementsInOrder});
 * {@link #distinct} drops the repeats (for {@code hasCompletedElements}).
 */
public final class ProcessPathIds {

    private ProcessPathIds() {
    }

    public static String[] inOrder(PathWalk<?, ?> path) {
        return path.getIds().toArray(String[]::new);
    }

    public static String[] inOrder(PathWalk.Trail path) {
        return path.getIds().toArray(String[]::new);
    }

    public static String[] distinct(PathWalk<?, ?> path) {
        return path.getDistinctIds().toArray(String[]::new);
    }

    public static String[] distinct(PathWalk.Trail path) {
        return path.getDistinctIds().toArray(String[]::new);
    }
}
