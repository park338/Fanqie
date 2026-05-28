package cn.fanqie.pomodoro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.fanqie.pomodoro.domain.SessionStatus;
import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.dto.ApiDtos.StatsTrendResponse;
import cn.fanqie.pomodoro.entity.InterruptionEntity;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsServiceTrendTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void reportsDailyStatsOldestToNewestForRequestedRange() {
        TimerSessionRepository timerSessions = mock(TimerSessionRepository.class);
        InterruptionRepository interruptions = mock(InterruptionRepository.class);
        TaskService tasks = mock(TaskService.class);
        StatsService stats = new StatsService(timerSessions, interruptions, tasks, clock);

        when(timerSessions.countByModeAndStatusAndStartedAtBetween(eq(TimerMode.POMODORO), eq(SessionStatus.COMPLETED), any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate day = ((LocalDateTime) invocation.getArgument(2)).toLocalDate();
                    return day.equals(LocalDate.of(2026, 5, 27)) ? 2L : 1L;
                });
        when(timerSessions.findByStartedAtBetweenOrderByStartedAtDesc(any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate day = ((LocalDateTime) invocation.getArgument(0)).toLocalDate();
                    return day.equals(LocalDate.of(2026, 5, 27))
                            ? List.of(session(25 * 60), session(10 * 60))
                            : List.of(session(25 * 60));
                });
        when(interruptions.findByOccurredAtBetweenOrderByOccurredAtDesc(any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate day = ((LocalDateTime) invocation.getArgument(0)).toLocalDate();
                    return day.equals(LocalDate.of(2026, 5, 27))
                            ? List.of(new InterruptionEntity(), new InterruptionEntity())
                            : List.of(new InterruptionEntity());
                });

        StatsTrendResponse trend = stats.trend(2);

        assertThat(trend.days()).hasSize(2);
        assertThat(trend.days().get(0).date()).isEqualTo(LocalDate.of(2026, 5, 27));
        assertThat(trend.days().get(0).completedPomodoros()).isEqualTo(2);
        assertThat(trend.days().get(0).sessions()).isEqualTo(2);
        assertThat(trend.days().get(0).interruptions()).isEqualTo(2);
        assertThat(trend.days().get(0).focusMinutes()).isEqualTo(35);
        assertThat(trend.days().get(1).date()).isEqualTo(LocalDate.of(2026, 5, 28));
        assertThat(trend.days().get(1).completedPomodoros()).isEqualTo(1);
    }

    private TimerSessionEntity session(int elapsedSeconds) {
        TimerSessionEntity entity = new TimerSessionEntity();
        entity.setElapsedSeconds(elapsedSeconds);
        return entity;
    }
}
