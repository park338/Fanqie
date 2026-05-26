package cn.fanqie.pomodoro.core;

import cn.fanqie.pomodoro.model.PomodoroConfig;
import cn.fanqie.pomodoro.model.SessionType;
import cn.fanqie.pomodoro.model.TimerStatus;

public interface PomodoroListener {
    void onStatusChanged(TimerStatus status);

    void onSessionChanged(SessionType sessionType, int totalSeconds, int remainingSeconds, int completedWorkSessions);

    void onTick(SessionType sessionType, int totalSeconds, int remainingSeconds, int completedWorkSessions);

    void onConfigChanged(PomodoroConfig config);
}

