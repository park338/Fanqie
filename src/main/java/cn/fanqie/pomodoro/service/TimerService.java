package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.domain.SessionStatus;
import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.domain.TimerRunStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStartRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStateDto;
import cn.fanqie.pomodoro.entity.SettingEntity;
import cn.fanqie.pomodoro.entity.TaskEntity;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import cn.fanqie.pomodoro.entity.TimerStateEntity;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import cn.fanqie.pomodoro.repository.TimerStateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimerService {
    private final SettingsService settingsService;
    private final TaskService taskService;
    private final TimerSessionRepository timerSessions;
    private final TimerStateRepository timerStates;
    private final StatsService statsService;
    private final Clock clock;

    public TimerService(
            SettingsService settingsService,
            TaskService taskService,
            TimerSessionRepository timerSessions,
            TimerStateRepository timerStates,
            StatsService statsService,
            Clock clock
    ) {
        this.settingsService = settingsService;
        this.taskService = taskService;
        this.timerSessions = timerSessions;
        this.timerStates = timerStates;
        this.statsService = statsService;
        this.clock = clock;
    }

    @Transactional
    public synchronized TimerStateDto current() {
        TimerStateEntity state = getOrCreateState();
        return toDto(state, "准备好就开始一个番茄。");
    }

    @Transactional
    public synchronized TimerStateDto start(TimerStartRequest request) {
        TimerStateEntity state = getOrCreateState();
        if (state.getStatus() == TimerRunStatus.PLAYING) {
            return toDto(state, "已经在专注中。");
        }
        if (request != null && request.mode() != null && state.getStatus() == TimerRunStatus.NEW) {
            resetForMode(state, request.mode());
        }
        LocalDateTime now = now();
        if (state.getSessionId() == null) {
            TimerSessionEntity session = new TimerSessionEntity();
            session.setMode(state.getMode());
            session.setStatus(SessionStatus.RUNNING);
            session.setTask(taskService.activeTaskOrNull());
            session.setStartedAt(now);
            session.setPlannedSeconds(state.getTotalSeconds());
            session.setElapsedSeconds(state.getElapsedSeconds());
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            state.setSessionId(timerSessions.save(session).getId());
        } else {
            TimerSessionEntity session = requireSession(state.getSessionId());
            session.setStatus(SessionStatus.RUNNING);
            session.touch(now);
        }
        state.setStatus(TimerRunStatus.PLAYING);
        state.setLastStartedAt(now);
        state.touch(now);
        return toDto(state, state.getMode() == TimerMode.POMODORO ? "小茄陪你盯住这一段。" : "休息也是计划的一部分。");
    }

    @Transactional
    public synchronized TimerStateDto pause() {
        TimerStateEntity state = getOrCreateState();
        if (state.getStatus() != TimerRunStatus.PLAYING) {
            return toDto(state, "当前没有运行中的计时。");
        }
        captureElapsed(state);
        state.setStatus(TimerRunStatus.PAUSED);
        state.touch(now());
        if (state.getSessionId() != null) {
            TimerSessionEntity session = requireSession(state.getSessionId());
            session.setStatus(SessionStatus.PAUSED);
            session.setElapsedSeconds(state.getElapsedSeconds());
            session.touch(now());
        }
        return toDto(state, "已暂停，回来后继续就好。");
    }

    @Transactional
    public synchronized TimerStateDto reset() {
        TimerStateEntity state = getOrCreateState();
        cancelOpenSession(state, "RESET");
        resetForMode(state, state.getMode());
        return toDto(state, "已重置当前计时。");
    }

    @Transactional
    public synchronized TimerStateDto skip() {
        TimerStateEntity state = getOrCreateState();
        captureElapsed(state);
        closeOpenSession(state, SessionStatus.SKIPPED, "SKIPPED");
        TimerMode next = state.getMode() == TimerMode.POMODORO ? TimerMode.SHORT_BREAK : TimerMode.POMODORO;
        resetForMode(state, next);
        return toDto(state, "已跳到下一段。");
    }

    @Transactional
    public synchronized TimerStateDto complete() {
        TimerStateEntity state = getOrCreateState();
        captureElapsed(state);
        TimerMode completedMode = state.getMode();
        closeOpenSession(state, SessionStatus.COMPLETED, "COMPLETED");
        if (completedMode == TimerMode.POMODORO) {
            taskService.incrementActiveCompletedPomodoros();
            SettingEntity settings = settingsService.getOrCreate();
            long completed = statsService.completedPomodorosToday();
            TimerMode next = completed > 0 && completed % settings.getLongBreakInterval() == 0
                    ? TimerMode.LONG_BREAK
                    : TimerMode.SHORT_BREAK;
            resetForMode(state, next);
            return toDto(state, next == TimerMode.LONG_BREAK ? "四个番茄到手，安排一个长休息。" : "番茄完成，短休息一下。");
        }
        resetForMode(state, TimerMode.POMODORO);
        return toDto(state, "休息结束，可以回到任务了。");
    }

    @Transactional
    public synchronized Long activeSessionIdOrNull() {
        return getOrCreateState().getSessionId();
    }

    private TimerStateDto toDto(TimerStateEntity state, String hint) {
        int remaining = remainingSeconds(state);
        TaskEntity activeTask = taskService.activeTaskOrNull();
        return new TimerStateDto(
                state.getMode(),
                state.getStatus(),
                state.getTotalSeconds(),
                remaining,
                state.getSessionId(),
                activeTask == null ? null : activeTask.getId(),
                activeTask == null ? null : activeTask.getTitle(),
                (int) statsService.completedPomodorosToday(),
                hint
        );
    }

    private TimerStateEntity getOrCreateState() {
        return timerStates.findById(TimerStateEntity.DEFAULT_ID)
                .orElseGet(() -> {
                    SettingEntity settings = settingsService.getOrCreate();
                    return timerStates.save(TimerStateEntity.initial(
                            TimerMode.POMODORO,
                            settingsService.durationSeconds(settings, TimerMode.POMODORO),
                            now()
                    ));
                });
    }

    private void resetForMode(TimerStateEntity state, TimerMode mode) {
        SettingEntity settings = settingsService.getOrCreate();
        LocalDateTime now = now();
        state.setMode(mode);
        state.setStatus(TimerRunStatus.NEW);
        state.setTotalSeconds(settingsService.durationSeconds(settings, mode));
        state.setElapsedSeconds(0);
        state.setSessionId(null);
        state.setLastStartedAt(null);
        state.touch(now);
    }

    private int remainingSeconds(TimerStateEntity state) {
        int elapsed = state.getElapsedSeconds();
        if (state.getStatus() == TimerRunStatus.PLAYING && state.getLastStartedAt() != null) {
            elapsed += secondsSince(state.getLastStartedAt(), now());
        }
        return Math.max(0, state.getTotalSeconds() - elapsed);
    }

    private void captureElapsed(TimerStateEntity state) {
        if (state.getStatus() == TimerRunStatus.PLAYING && state.getLastStartedAt() != null) {
            LocalDateTime now = now();
            int elapsed = state.getElapsedSeconds() + secondsSince(state.getLastStartedAt(), now);
            state.setElapsedSeconds(Math.min(elapsed, state.getTotalSeconds()));
            state.setLastStartedAt(now);
            state.touch(now);
        }
    }

    private void cancelOpenSession(TimerStateEntity state, String reason) {
        captureElapsed(state);
        closeOpenSession(state, SessionStatus.CANCELLED, reason);
    }

    private void closeOpenSession(TimerStateEntity state, SessionStatus status, String reason) {
        if (state.getSessionId() == null) {
            return;
        }
        LocalDateTime now = now();
        TimerSessionEntity session = requireSession(state.getSessionId());
        session.setStatus(status);
        session.setElapsedSeconds(status == SessionStatus.COMPLETED ? session.getPlannedSeconds() : state.getElapsedSeconds());
        session.setEndedAt(now);
        session.setCompletionReason(reason);
        session.touch(now);
    }

    private TimerSessionEntity requireSession(Long sessionId) {
        return timerSessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "计时会话不存在"));
    }

    private int secondsSince(LocalDateTime start, LocalDateTime end) {
        long seconds = Duration.between(start, end).getSeconds();
        return seconds < 0 ? 0 : (int) seconds;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
