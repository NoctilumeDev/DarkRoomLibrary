package org.darkroomlibrary.domain.type;

/**
 * Whether a user is blocked from posting content.
 */
public enum MuteStatus {

    ACTIVE(false, "可用"),
    DISABLED(true, "禁言状态");

    private final boolean muted;
    private final String displayName;

    MuteStatus(boolean muted, String displayName) {
        this.muted = muted;
        this.displayName = displayName;
    }

    public Boolean muted() {
        return muted;
    }

    public String displayName() {
        return displayName;
    }
}
