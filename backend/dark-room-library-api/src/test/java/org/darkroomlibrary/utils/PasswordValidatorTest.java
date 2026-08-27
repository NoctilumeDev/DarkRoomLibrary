package org.darkroomlibrary.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordValidatorTest {

    @Test
    void acceptsPublishedLengthBoundaryAndAnyThreeSupportedClasses() {
        assertTrue(PasswordValidator.isValid("Aa1!" + "x".repeat(16)));
        assertTrue(PasswordValidator.isValid("abcDEF12"));
        assertTrue(PasswordValidator.isValid("abcdef1!"));
        assertTrue(PasswordValidator.isValid("ABCDEF1!"));
        assertTrue(PasswordValidator.isValid("abcDEF!@"));
    }

    @Test
    void rejectsValuesOutsidePublishedLengthBoundary() {
        assertFalse(PasswordValidator.isValid(null));
        assertFalse(PasswordValidator.isValid("Aa1!abc"));
        assertFalse(PasswordValidator.isValid("Aa1!" + "x".repeat(17)));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertFalse(PasswordValidator.isValid("Aa1!" + "x".repeat(999_996))));
    }

    @Test
    void preservesAsciiCharacterClassContract() {
        assertFalse(PasswordValidator.isValid("abcdef١!"));
        assertTrue(PasswordValidator.isValid("Abcdef1?"));
    }
}
