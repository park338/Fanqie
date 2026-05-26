package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.domain.SessionStatus;
import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.dto.ApiDtos.StatsResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerSessionSummary;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {
    private final TimerSessionRepository timerSessions;
    private final InterruptionRepository interruptions;
    private final TaskService tasks;
    private final Clock clock;

    public StatsService(
            TimerSessionRepository timerSessions,
            InterruptionRepository interruptions,
            TaskService tasks,
            Clock clock
    ) {
        this.timerSessions = timerSessions;
        this.interruptions = interruptions;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatsResponse today() {
        LocalDateTime start = LocalDate.now(clock).atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long completedPomodoros = completedPomodorosBetween(start, end);
        long sessionsToday = timerSessions.findByStartedAtBetweenOrderByStartedAtDesc(start, end).size();
        long interruptionsToday = interruptions.findByOccurredAtBetweenOrderByOccurredAtDesc(start, end).size();
        return new StatsResponse(
                completedPomodoros,
                sessionsToday,
                interruptionsToday,
                tasks.unfinishedCount(),
                timerSessions.findTop10ByOrderByStartedAtDesc().stream().map(this::toSummary).toList()
        );
    }

    @Transactional(readOnly = true)
    public long completedPomodorosToday() {
        LocalDateTime start = LocalDate.now(clock).atStartOfDay();
        return completedPomodorosBetween(start, start.plusDays(1));
    }

    private long completedPomodorosBetween(LocalDateTime start, LocalDateTime end) {
        return timerSessions.countByModeAndStatusAndStartedAtBetween(
                TimerMode.POMODORO,
                SessionStatus.COMPLETED,
                start,
                end
        );
    }

    private TimerSessionSummary toSummary(TimerSessionEntity entity) {
        return new TimerSessionSummary(
                entity.getId(),
                entity.getMode(),
                entity.getStatus().name(),
                entity.getTask() == null ? null : entity.getTask().getTitle(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getElapsedSeconds()
        );
    }
}
