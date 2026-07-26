package org.darkroomlibrary.utils;

/**
 * 密码强度校验器
 * 规则：至少8位，包含大小写字母、数字、特殊字符中的至少3种
 */
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        int score = 0;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;
        return score >= 3;
    }

    public static String getRequirement() {
        return "密码至少8位，需包含大写字母、小写字母、数字、特殊字符中的至少3种";
    }
}