package org.darkroomlibrary.domain.type;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum UserRole {
    SUPER_ADMIN(0, "超级管理员", true),
    ADMIN(1, "管理员", true),
    READER(2, "读者", false),
    ACQUISITIONS(3, "采购员", false),
    LOGISTICS(4, "物流员", false);

    private static final Map<Integer, UserRole> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(UserRole::code, Function.identity()));
    private static final Map<String, UserRole> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(UserRole::displayName, Function.identity()));

    private final int code;
    private final String displayName;
    private final boolean administrator;

    UserRole(int code, String displayName, boolean administrator) {
        this.code = code;
        this.displayName = displayName;
        this.administrator = administrator;
    }

    public Integer code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isAdministrator() {
        return administrator;
    }

    public static Optional<UserRole> fromCode(Integer code) {
        return Optional.ofNullable(code).map(BY_CODE::get);
    }

    public static String displayNameOf(Integer code) {
        return fromCode(code).map(UserRole::displayName).orElse(null);
    }

    public static Integer codeOf(String displayName) {
        UserRole role = BY_NAME.get(displayName);
        return role == null ? null : role.code();
    }
}
