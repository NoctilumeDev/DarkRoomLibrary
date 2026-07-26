package org.darkroomlibrary.domain.type;

/**
 * Account lifecycle state stored in the user table.
 */
public enum AccountStatus {

    NORMAL(0, "正常"),
    FROZEN(1, "冻结"),
    CANCELLED(2, "已注销");

    private final int code;
    private final String displayName;

    AccountStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public Integer code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }
}
