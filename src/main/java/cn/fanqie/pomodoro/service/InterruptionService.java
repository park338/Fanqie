package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.dto.ApiDtos.CreateInterruptionRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.InterruptionDto;
import cn.fanqie.pomodoro.entity.InterruptionEntity;
import cn.fanqie.pomodoro.entity.TaskEntity;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterruptionService {
    private final InterruptionRepository interruptions;
    private final TimerSessionRepository timerSessions;
    private final TaskService tasks;
    private final TimerService timer;
    private final Clock clock;

    public InterruptionService(
            InterruptionRepository interruptions,
            TimerSessionRepository timerSessions,
            TaskService tasks,
            TimerService timer,
            Clock clock
    ) {
        this.interruptions = interruptions;
        this.timerSessions = timerSessions;
        this.tasks = tasks;
        this.timer = timer;
        this.clock = clock;
    }

    @Transactional
    public InterruptionDto record(CreateInterruptionRequest request) {
        LocalDateTime now = now();
        InterruptionEntity entity = new InterruptionEntity();
        entity.setNote(normalizeNote(request == null ? null : request.note()));
        entity.setTask(resolveTask(request == null ? null : request.taskId()));
        Long sessionId = timer.activeSessionIdOrNull();
        entity.setTimerSession(sessionId == null ? null : timerSessions.findById(sessionId).orElse(null));
        entity.setOccurredAt(now);
        entity.setCreatedAt(now);
        return toDto(interruptions.save(entity));
    }

    @Transactional(readOnly = true)
    public List<InterruptionDto> today() {
        LocalDateTime start = LocalDate.now(clock).atStartOfDay();
        return interruptions.findByOccurredAtBetweenOrderByOccurredAtDesc(start, start.plusDays(1))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public InterruptionDto toDto(InterruptionEntity entity) {
        TaskEntity task = entity.getTask();
        TimerSessionEntity session = entity.getTimerSession();
        return new InterruptionDto(
                entity.getId(),
                entity.getNote(),
                task == null ? null : task.getId(),
                task == null ? null : task.getTitle(),
                session == null ? null : session.getId(),
                entity.getOccurredAt()
        );
    }

    private TaskEntity resolveTask(Long taskId) {
        if (taskId != null) {
            return tasks.requireTask(taskId);
        }
        return tasks.activeTaskOrNull();
    }

    private String normalizeNote(String note) {
        String value = note == null ? "" : note.trim();
        return value.isBlank() ? "记录了一次打断" : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
