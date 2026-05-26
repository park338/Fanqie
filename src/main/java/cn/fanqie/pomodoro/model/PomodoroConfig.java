package cn.fanqie.pomodoro.model;

public final class PomodoroConfig {
    public static final int MIN_SECONDS = 1;

    private final int workSeconds;
    private final int shortBreakSeconds;
    private final int longBreakSeconds;
    private final int longBreakEveryWorkSessions;

    public PomodoroConfig(int workSeconds, int shortBreakSeconds, int longBreakSeconds, int longBreakEveryWorkSessions) {
        this.workSeconds = requirePositive(workSeconds, "workSeconds");
        this.shortBreakSeconds = requirePositive(shortBreakSeconds, "shortBreakSeconds");
        this.longBreakSeconds = requirePositive(longBreakSeconds, "longBreakSeconds");
        this.longBreakEveryWorkSessions = requirePositive(longBreakEveryWorkSessions, "longBreakEveryWorkSessions");
    }

    public static PomodoroConfig defaultConfig() {
        return new PomodoroConfig(25 * 60, 5 * 60, 15 * 60, 4);
    }

    public int getWorkSeconds() {
        return workSeconds;
    }

    public int getShortBreakSeconds() {
        return shortBreakSeconds;
    }

    public int getLongBreakSeconds() {
        return longBreakSeconds;
    }

    public int getLongBreakEveryWorkSessions() {
        return longBreakEveryWorkSessions;
    }

    private static int requirePositive(int value, String name) {
        if (value < MIN_SECONDS) {
            throw new IllegalArgumentException(name + " must be >= " + MIN_SECONDS + ", but was " + value);
        }
        return value;
    }
}

