package cn.fanqie.pomodoro.domain;

public enum TimerMode {
    POMODORO("番茄钟"),
    SHORT_BREAK("短休息"),
    LONG_BREAK("长休息");

    private final String displayName;

    TimerMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
