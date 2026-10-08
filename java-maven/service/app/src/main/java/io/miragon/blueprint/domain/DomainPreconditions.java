package io.miragon.blueprint.domain;

/**
 * Input validation shared by the domain's value objects. It lives in plain static methods rather than in
 * the records' compact constructors on purpose: PIT does not mutate a record's canonical constructor, so
 * checks placed there would escape the mutation gate.
 */
public final class DomainPreconditions {

    private DomainPreconditions() {
    }

    /**
     * Returns {@code value} unchanged, or throws {@link IllegalArgumentException} with {@code message} when it is
     * {@code null}, empty or whitespace only. Whitespace includes the no-break spaces ({@code U+00A0},
     * {@code U+2007}, {@code U+202F}), which {@link String#isBlank()} would let through.
     */
    public static String requireNotBlank(String value, String message) {
        if (value == null || value.chars().allMatch(DomainPreconditions::isWhitespace)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** Returns {@code value} unchanged, or throws {@link IllegalArgumentException} with {@code message} when it is {@code null}. */
    public static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean isWhitespace(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }
}
