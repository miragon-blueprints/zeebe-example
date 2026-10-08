package io.miragon.common.architecture;

import org.junit.jupiter.api.Nested;

/**
 * A single, ready-to-use <strong>ArchUnit</strong> architecture suite for a service.
 *
 * <p>A service wires up the full suite with one class:
 *
 * <pre>{@code
 * class ArchitectureTest extends ServiceArchitectureTest {
 *     ArchitectureTest() {
 *         super("io.miragon.blueprint");
 *     }
 * }
 * }</pre>
 *
 * <p>ArchUnit reads compiled <strong>bytecode</strong>, so it sees the fully resolved dependency graph. It
 * owns the <em>dependency &amp; structure</em> rules — hexagonal layering, technology-neutrality of domain
 * &amp; application, port/adapter isolation ({@link Dependencies}), naming conventions ({@link Naming}),
 * freedom of cycles and the no-{@code System.out} check ({@link CodingGuidelines}).
 *
 * <p>The two <em>source-structure</em> rules the compiler erases — one top-level type per file and no
 * wildcard imports — are enforced by Checkstyle in the Maven build ({@code config/checkstyle}).
 *
 * <p>This module is <strong>self-contained</strong>: it carries the ArchUnit dependency and its own copies
 * of the rules, so it can be dropped into a service as a single test dependency.
 */
public abstract class ServiceArchitectureTest {

    private final String rootPackage;

    protected ServiceArchitectureTest(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    @Nested
    class Dependencies extends HexagonalArchitectureTest {
        Dependencies() {
            super(ServiceArchitectureTest.this.rootPackage);
        }
    }

    @Nested
    class Naming extends NamingConventionArchitectureTest {
        Naming() {
            super(ServiceArchitectureTest.this.rootPackage);
        }
    }

    @Nested
    class CodingGuidelines extends BasicCodingGuidelinesTest {
        CodingGuidelines() {
            super(ServiceArchitectureTest.this.rootPackage);
        }
    }
}
