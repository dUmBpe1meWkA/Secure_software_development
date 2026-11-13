package mtuci.task2.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import mtuci.task2.exception.ApiException;

public final class PasswordPolicy {
    private static final String REQUIREMENTS = "Пароль: от 10 символов и не более 72 байт, строчная и заглавная буквы, "
            + "цифра, спецсимвол; без частых слов и частей имени или email";
    private static final List<String> COMMON = List.of("password", "qwerty", "123456", "admin", "parking", "пароль");

    private PasswordPolicy() {}

    /** BCrypt ignores everything past 72 bytes, so longer passwords are rejected instead of being cut. */
    public static void check(String password, String email, String name) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)
                || containsPersonalOrCommon(password.toLowerCase(Locale.ROOT), email, name)) {
            throw ApiException.badRequest(REQUIREMENTS);
        }
    }

    private static boolean containsPersonalOrCommon(String password, String email, String name) {
        if (COMMON.stream().anyMatch(password::contains)) return true;
        String local = email == null ? "" : email.substring(0, email.indexOf('@'));
        return contains(password, local) || contains(password, name);
    }

    private static boolean contains(String password, String part) {
        return part != null && part.strip().length() >= 3 && password.contains(part.strip().toLowerCase(Locale.ROOT));
    }
}
