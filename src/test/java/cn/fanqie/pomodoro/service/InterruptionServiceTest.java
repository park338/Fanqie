package cn.fanqie.pomodoro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.fanqie.pomodoro.dto.ApiDtos.CreateInterruptionRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.InterruptionDto;
import cn.fanqie.pomodoro.entity.InterruptionEntity;
import cn.fanqie.pomodoro.entity.TaskEntity;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterruptionServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T07:30:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void recordsInterruptionAgainstActiveTaskAndTimerSession() {
        InterruptionRepository interruptions = mock(InterruptionRepository.class);
        TimerSessionRepository timerSessions = mock(TimerSessionRepository.class);
        TaskService tasks = mock(TaskService.class);
        TimerService timer = mock(TimerService.class);
        TaskEntity activeTask = new TaskEntity();
        TimerSessionEntity activeSession = new TimerSessionEntity();

        ReflectionTestUtils.setField(activeTask, "id", 2L);
        activeTask.setTitle("写实现");
        ReflectionTestUtils.setField(activeSession, "id", 7L);

        when(tasks.activeTaskOrNull()).thenReturn(activeTask);
        when(timer.activeSessionIdOrNull()).thenReturn(7L);
        when(timerSessions.findById(7L)).thenReturn(Optional.of(activeSession));
        when(interruptions.save(any(InterruptionEntity.class))).thenAnswer(invocation -> {
            InterruptionEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 3L);
            return entity;
        });

        InterruptionService service = new InterruptionService(interruptions, timerSessions, tasks, timer, clock);

        InterruptionDto recorded = service.record(new CreateInterruptionRequest("同事打断，稍后处理", null));

        assertThat(recorded.id()).isEqualTo(3L);
        assertThat(recorded.note()).isEqualTo("同事打断，稍后处理");
        assertThat(recorded.taskId()).isEqualTo(2L);
        assertThat(recorded.taskTitle()).isEqualTo("写实现");
        assertThat(recorded.timerSessionId()).isEqualTo(7L);
        assertThat(recorded.occurredAt()).isEqualTo(java.time.LocalDateTime.now(clock));
    }
}
