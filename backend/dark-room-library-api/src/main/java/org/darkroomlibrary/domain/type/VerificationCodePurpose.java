package org.darkroomlibrary.domain.type;

import java.util.Arrays;
import java.util.Optional;

public enum VerificationCodePurpose {

    REGISTER,
    RESET_PASSWORD,
    CHANGE_EMAIL;

    public static Optional<VerificationCodePurpose> from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(purpose -> purpose.name().equals(normalized))
                .findFirst();
    }
}
