package cn.fanqie.pomodoro.model;

public enum SessionType {
    WORK("专注"),
    SHORT_BREAK("短休息"),
    LONG_BREAK("长休息");

    private final String displayName;

    SessionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

