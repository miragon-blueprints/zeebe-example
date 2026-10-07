package io.miragon.blueprint.domain.leasing;

import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern EMAIL_REGEX = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        requireValid(value);
    }

    // A static method rather than inline in the compact constructor, so mutation testing covers the check.
    private static void requireValid(String value) {
        if (value == null || !EMAIL_REGEX.matcher(value).matches()) {
            throw new IllegalArgumentException("'" + value + "' is not a valid email address");
        }
    }
}
