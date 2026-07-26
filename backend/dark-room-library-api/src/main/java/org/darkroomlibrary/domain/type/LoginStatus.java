package org.darkroomlibrary.domain.type;

/**
 * Whether an account may authenticate.
 */
public enum LoginStatus {

    ACTIVE(false, "可登录"),
    DISABLED(true, "登录状态异常");

    private final boolean disabled;
    private final String displayName;

    LoginStatus(boolean disabled, String displayName) {
        this.disabled = disabled;
        this.displayName = displayName;
    }

    public Boolean disabled() {
        return disabled;
    }

    public String displayName() {
        return displayName;
    }
}
