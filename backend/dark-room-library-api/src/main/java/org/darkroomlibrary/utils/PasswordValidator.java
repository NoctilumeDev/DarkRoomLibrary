package org.darkroomlibrary.utils;

/**
 * 密码强度校验器
 * 规则：8-20位，包含大小写字母、数字、特殊字符中的至少3种
 */
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 20;
    private static final String SPECIAL_CHARACTERS = "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?";

    public static boolean isValid(String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH) {
            return false;
        }

        boolean hasLowercase = false;
        boolean hasUppercase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            if (character == '\n'
                    || character == '\r'
                    || character == '\u0085'
                    || character == '\u2028'
                    || character == '\u2029') {
                return false;
            }
            if (character >= 'a' && character <= 'z') {
                hasLowercase = true;
            } else if (character >= 'A' && character <= 'Z') {
                hasUppercase = true;
            } else if (character >= '0' && character <= '9') {
                hasDigit = true;
            } else if (SPECIAL_CHARACTERS.indexOf(character) >= 0) {
                hasSpecial = true;
            }
        }

        int score = 0;
        if (hasLowercase) score++;
        if (hasUppercase) score++;
        if (hasDigit) score++;
        if (hasSpecial) score++;
        return score >= 3;
    }

    public static String getRequirement() {
        return "密码需为8-20位，并包含大写字母、小写字母、数字、特殊字符中的至少3种";
    }
}
