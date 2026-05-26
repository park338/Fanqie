package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.domain.SessionStatus;
import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.domain.TimerRunStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStartRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStateDto;
import cn.fanqie.pomodoro.entity.SettingEntity;
import cn.fanqie.pomodoro.entity.TaskEntity;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
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
    private final StatsService statsService;
    private final Clock clock;
    private final TimerState state = new TimerState();

    public TimerService(
            SettingsService settingsService,
            TaskService taskService,
            TimerSessionRepository timerSessions,
            StatsService statsService,
            Clock clock
    ) {
        this.settingsService = settingsService;
        this.taskService = taskService;
        this.timerSessions = timerSessions;
        this.statsService = statsService;
        this.clock = clock;
    }

    @Transactional
    public synchronized TimerStateDto current() {
        ensureInitialized();
        return toDto("准备好就开始一个番茄。");
    }

    @Transactional
    public synchronized TimerStateDto start(TimerStartRequest request) {
        ensureInitialized();
        if (state.status == TimerRunStatus.PLAYING) {
            return toDto("已经在专注中。");
        }
        if (request != null && request.mode() != null && state.status == TimerRunStatus.NEW) {
            resetForMode(request.mode());
        }
        LocalDateTime now = now();
        if (state.sessionId == null) {
            TimerSessionEntity session = new TimerSessionEntity();
            session.setMode(state.mode);
            session.setStatus(SessionStatus.RUNNING);
            session.setTask(taskService.activeTaskOrNull());
            session.setStartedAt(now);
            session.setPlannedSeconds(state.totalSeconds);
            session.setElapsedSeconds(state.elapsedSeconds);
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            state.sessionId = timerSessions.save(session).getId();
        } else {
            TimerSessionEntity session = requireSession(state.sessionId);
            session.setStatus(SessionStatus.RUNNING);
            session.touch(now);
        }
        state.status = TimerRunStatus.PLAYING;
        state.lastStartedAt = now;
        return toDto(state.mode == TimerMode.POMODORO ? "小茄陪你盯住这一段。" : "休息也是计划的一部分。");
    }

    @Transactional
    public synchronized TimerStateDto pause() {
        ensureInitialized();
        if (state.status != TimerRunStatus.PLAYING) {
            return toDto("当前没有运行中的计时。");
        }
        captureElapsed();
        state.status = TimerRunStatus.PAUSED;
        if (state.sessionId != null) {
            TimerSessionEntity session = requireSession(state.sessionId);
            session.setStatus(SessionStatus.PAUSED);
            session.setElapsedSeconds(state.elapsedSeconds);
            session.touch(now());
        }
        return toDto("已暂停，回来后继续就好。");
    }

    @Transactional
    public synchronized TimerStateDto reset() {
        ensureInitialized();
        cancelOpenSession("RESET");
        resetForMode(state.mode);
        return toDto("已重置当前计时。");
    }

    @Transactional
    public synchronized TimerStateDto skip() {
        ensureInitialized();
        captureElapsed();
        closeOpenSession(SessionStatus.SKIPPED, "SKIPPED");
        TimerMode next = state.mode == TimerMode.POMODORO ? TimerMode.SHORT_BREAK : TimerMode.POMODORO;
        resetForMode(next);
        return toDto("已跳到下一段。");
    }

    @Transactional
    public synchronized TimerStateDto complete() {
        ensureInitialized();
        captureElapsed();
        TimerMode completedMode = state.mode;
        closeOpenSession(SessionStatus.COMPLETED, "COMPLETED");
        if (completedMode == TimerMode.POMODORO) {
            taskService.incrementActiveCompletedPomodoros();
            SettingEntity settings = settingsService.getOrCreate();
            long completed = statsService.completedPomodorosToday();
            TimerMode next = completed > 0 && completed % settings.getLongBreakInterval() == 0
                    ? TimerMode.LONG_BREAK
                    : TimerMode.SHORT_BREAK;
            resetForMode(next);
            return toDto(next == TimerMode.LONG_BREAK ? "四个番茄到手，安排一个长休息。" : "番茄完成，短休息一下。");
        }
        resetForMode(TimerMode.POMODORO);
        return toDto("休息结束，可以回到任务了。");
    }

    private TimerStateDto toDto(String hint) {
        int remaining = remainingSeconds();
        TaskEntity activeTask = taskService.activeTaskOrNull();
        return new TimerStateDto(
                state.mode,
                state.status,
                state.totalSeconds,
                remaining,
                state.sessionId,
                activeTask == null ? null : activeTask.getId(),
                activeTask == null ? null : activeTask.getTitle(),
                (int) statsService.completedPomodorosToday(),
                hint
        );
    }

    private void ensureInitialized() {
        if (state.initialized) {
            return;
        }
        resetForMode(TimerMode.POMODORO);
        state.initialized = true;
    }

    private void resetForMode(TimerMode mode) {
        SettingEntity settings = settingsService.getOrCreate();
        state.mode = mode;
        state.status = TimerRunStatus.NEW;
        state.totalSeconds = settingsService.durationSeconds(settings, mode);
        state.elapsedSeconds = 0;
        state.sessionId = null;
        state.lastStartedAt = null;
    }

    private int remainingSeconds() {
        int elapsed = state.elapsedSeconds;
        if (state.status == TimerRunStatus.PLAYING && state.lastStartedAt != null) {
            elapsed += secondsSince(state.lastStartedAt, now());
        }
        return Math.max(0, state.totalSeconds - elapsed);
    }

    private void captureElapsed() {
        if (state.status == TimerRunStatus.PLAYING && state.lastStartedAt != null) {
            state.elapsedSeconds += secondsSince(state.lastStartedAt, now());
            state.elapsedSeconds = Math.min(state.elapsedSeconds, state.totalSeconds);
            state.lastStartedAt = now();
        }
    }

    private void cancelOpenSession(String reason) {
        captureElapsed();
        closeOpenSession(SessionStatus.CANCELLED, reason);
    }

    private void closeOpenSession(SessionStatus status, String reason) {
        if (state.sessionId == null) {
            return;
        }
        LocalDateTime now = now();
        TimerSessionEntity session = requireSession(state.sessionId);
        session.setStatus(status);
        session.setElapsedSeconds(status == SessionStatus.COMPLETED ? session.getPlannedSeconds() : state.elapsedSeconds);
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

    private static final class TimerState {
        private boolean initialized;
        private TimerMode mode;
        private TimerRunStatus status;
        private int totalSeconds;
        private int elapsedSeconds;
        private Long sessionId;
        private LocalDateTime lastStartedAt;
    }
}
