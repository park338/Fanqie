package cn.fanqie.pomodoro.core;

import cn.fanqie.pomodoro.model.PomodoroConfig;
import cn.fanqie.pomodoro.model.SessionType;
import cn.fanqie.pomodoro.model.TimerStatus;

import java.util.ArrayList;
import java.util.List;

public final class PomodoroTimer {
    private PomodoroConfig config;
    private final List<PomodoroListener> listeners;

    private TimerStatus status;
    private SessionType sessionType;
    private int totalSeconds;
    private int remainingSeconds;
    private int completedWorkSessions;

    public PomodoroTimer(PomodoroConfig config) {
        this.config = requireNonNull(config, "config");
        this.listeners = new ArrayList<PomodoroListener>();
        resetToIdle();
    }

    public PomodoroConfig getConfig() {
        return config;
    }

    public TimerStatus getStatus() {
        return status;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public int getTotalSeconds() {
        return totalSeconds;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public int getCompletedWorkSessions() {
        return completedWorkSessions;
    }

    public void addListener(PomodoroListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        listener.onConfigChanged(config);
        listener.onStatusChanged(status);
        listener.onSessionChanged(sessionType, totalSeconds, remainingSeconds, completedWorkSessions);
    }

    public void removeListener(PomodoroListener listener) {
        listeners.remove(listener);
    }

    public void updateConfig(PomodoroConfig newConfig) {
        this.config = requireNonNull(newConfig, "newConfig");
        notifyConfigChanged();
        if (status == TimerStatus.IDLE) {
            setSession(SessionType.WORK, config.getWorkSeconds());
            notifySessionChanged();
        }
    }

    public void start() {
        if (status == TimerStatus.RUNNING) {
            return;
        }
        if (status == TimerStatus.IDLE) {
            setSession(SessionType.WORK, config.getWorkSeconds());
            notifySessionChanged();
        }
        setStatus(TimerStatus.RUNNING);
    }

    public void pause() {
        if (status != TimerStatus.RUNNING) {
            return;
        }
        setStatus(TimerStatus.PAUSED);
    }

    public void resume() {
        if (status != TimerStatus.PAUSED) {
            return;
        }
        setStatus(TimerStatus.RUNNING);
    }

    public void reset() {
        resetToIdle();
        notifyStatusChanged();
        notifySessionChanged();
    }

    public void skipToNextSession() {
        if (status == TimerStatus.IDLE) {
            return;
        }
        advanceSession();
        notifySessionChanged();
        notifyTick();
    }

    public void tick() {
        if (status != TimerStatus.RUNNING) {
            return;
        }
        if (remainingSeconds <= 0) {
            advanceSession();
            notifySessionChanged();
            notifyTick();
            return;
        }

        remainingSeconds--;
        notifyTick();

        if (remainingSeconds <= 0) {
            advanceSession();
            notifySessionChanged();
            notifyTick();
        }
    }

    private void advanceSession() {
        if (sessionType == SessionType.WORK) {
            completedWorkSessions++;
            if (completedWorkSessions % config.getLongBreakEveryWorkSessions() == 0) {
                setSession(SessionType.LONG_BREAK, config.getLongBreakSeconds());
            } else {
                setSession(SessionType.SHORT_BREAK, config.getShortBreakSeconds());
            }
        } else {
            setSession(SessionType.WORK, config.getWorkSeconds());
        }
    }

    private void resetToIdle() {
        this.status = TimerStatus.IDLE;
        this.completedWorkSessions = 0;
        setSession(SessionType.WORK, config.getWorkSeconds());
    }

    private void setStatus(TimerStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }
        this.status = newStatus;
        notifyStatusChanged();
    }

    private void setSession(SessionType newType, int seconds) {
        this.sessionType = newType;
        this.totalSeconds = seconds;
        this.remainingSeconds = seconds;
    }

    private void notifyStatusChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onStatusChanged(status);
        }
    }

    private void notifySessionChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onSessionChanged(sessionType, totalSeconds, remainingSeconds, completedWorkSessions);
        }
    }

    private void notifyTick() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onTick(sessionType, totalSeconds, remainingSeconds, completedWorkSessions);
        }
    }

    private void notifyConfigChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onConfigChanged(config);
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

