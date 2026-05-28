package cn.fanqie.pomodoro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.fanqie.pomodoro.domain.SessionStatus;
import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.domain.TimerRunStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStartRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStateDto;
import cn.fanqie.pomodoro.entity.SettingEntity;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import cn.fanqie.pomodoro.entity.TimerStateEntity;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import cn.fanqie.pomodoro.repository.TimerStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TimerServicePersistenceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T07:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void currentRestoresPlayingSessionFromPersistedTimerState() {
        SettingsService settings = mock(SettingsService.class);
        TaskService tasks = mock(TaskService.class);
        StatsService stats = mock(StatsService.class);
        TimerSessionRepository timerSessions = mock(TimerSessionRepository.class);
        TimerStateRepository timerStates = mock(TimerStateRepository.class);
        AtomicReference<TimerStateEntity> stateStore = new AtomicReference<>();
        AtomicReference<TimerSessionEntity> sessionStore = new AtomicReference<>();
        SettingEntity defaults = SettingEntity.defaults(java.time.LocalDateTime.now(clock));

        when(settings.getOrCreate()).thenReturn(defaults);
        when(settings.durationSeconds(defaults, TimerMode.POMODORO)).thenReturn(25 * 60);
        when(stats.completedPomodorosToday()).thenReturn(0L);
        when(timerStates.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(stateStore.get()));
        when(timerStates.save(any(TimerStateEntity.class))).thenAnswer(invocation -> {
            TimerStateEntity entity = invocation.getArgument(0);
            stateStore.set(entity);
            return entity;
        });
        when(timerSessions.save(any(TimerSessionEntity.class))).thenAnswer(invocation -> {
            TimerSessionEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 1L);
            sessionStore.set(entity);
            return entity;
        });
        when(timerSessions.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(sessionStore.get()));

        TimerService firstInstance = new TimerService(settings, tasks, timerSessions, timerStates, stats, clock);
        TimerStateDto started = firstInstance.start(new TimerStartRequest(TimerMode.POMODORO));

        TimerService restartedInstance = new TimerService(settings, tasks, timerSessions, timerStates, stats, clock);
        TimerStateDto restored = restartedInstance.current();

        assertThat(started.sessionId()).isEqualTo(1L);
        assertThat(sessionStore.get().getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(restored.status()).isEqualTo(TimerRunStatus.PLAYING);
        assertThat(restored.mode()).isEqualTo(TimerMode.POMODORO);
        assertThat(restored.sessionId()).isEqualTo(1L);
        assertThat(restored.remainingSeconds()).isEqualTo(25 * 60);
    }
}
