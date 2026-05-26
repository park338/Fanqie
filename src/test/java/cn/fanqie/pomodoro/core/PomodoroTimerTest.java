package cn.fanqie.pomodoro.core;

import cn.fanqie.pomodoro.model.PomodoroConfig;
import cn.fanqie.pomodoro.model.SessionType;
import cn.fanqie.pomodoro.model.TimerStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PomodoroTimerTest {
    @Test
    public void workToShortBreakToWork() {
        PomodoroConfig config = new PomodoroConfig(3, 2, 4, 2);
        PomodoroTimer timer = new PomodoroTimer(config);

        assertEquals(TimerStatus.IDLE, timer.getStatus());
        timer.start();
        assertEquals(TimerStatus.RUNNING, timer.getStatus());
        assertEquals(SessionType.WORK, timer.getSessionType());
        assertEquals(3, timer.getRemainingSeconds());

        timer.tick();
        assertEquals(2, timer.getRemainingSeconds());
        timer.tick();
        assertEquals(1, timer.getRemainingSeconds());
        timer.tick();

        assertEquals(SessionType.SHORT_BREAK, timer.getSessionType());
        assertEquals(2, timer.getRemainingSeconds());
        assertEquals(1, timer.getCompletedWorkSessions());

        timer.tick();
        assertEquals(1, timer.getRemainingSeconds());
        timer.tick();

        assertEquals(SessionType.WORK, timer.getSessionType());
        assertEquals(3, timer.getRemainingSeconds());
        assertEquals(1, timer.getCompletedWorkSessions());
    }

    @Test
    public void longBreakEveryNWorkSessions() {
        PomodoroConfig config = new PomodoroConfig(2, 1, 4, 2);
        PomodoroTimer timer = new PomodoroTimer(config);
        timer.start();

        timer.tick();
        timer.tick();
        assertEquals(SessionType.SHORT_BREAK, timer.getSessionType());
        assertEquals(1, timer.getCompletedWorkSessions());

        timer.tick();
        assertEquals(SessionType.WORK, timer.getSessionType());

        timer.tick();
        timer.tick();
        assertEquals(SessionType.LONG_BREAK, timer.getSessionType());
        assertEquals(2, timer.getCompletedWorkSessions());
        assertEquals(4, timer.getRemainingSeconds());
    }

    @Test
    public void pauseAndResume() {
        PomodoroConfig config = new PomodoroConfig(5, 1, 2, 2);
        PomodoroTimer timer = new PomodoroTimer(config);
        timer.start();
        timer.tick();
        assertEquals(4, timer.getRemainingSeconds());

        timer.pause();
        assertEquals(TimerStatus.PAUSED, timer.getStatus());
        timer.tick();
        assertEquals(4, timer.getRemainingSeconds());

        timer.resume();
        assertEquals(TimerStatus.RUNNING, timer.getStatus());
        timer.tick();
        assertEquals(3, timer.getRemainingSeconds());
    }

    @Test
    public void resetReturnsToIdle() {
        PomodoroConfig config = new PomodoroConfig(3, 2, 4, 2);
        PomodoroTimer timer = new PomodoroTimer(config);
        timer.start();
        timer.tick();
        timer.tick();
        timer.tick();
        assertEquals(SessionType.SHORT_BREAK, timer.getSessionType());
        assertEquals(1, timer.getCompletedWorkSessions());

        timer.reset();
        assertEquals(TimerStatus.IDLE, timer.getStatus());
        assertEquals(SessionType.WORK, timer.getSessionType());
        assertEquals(3, timer.getRemainingSeconds());
        assertEquals(0, timer.getCompletedWorkSessions());
    }
}
