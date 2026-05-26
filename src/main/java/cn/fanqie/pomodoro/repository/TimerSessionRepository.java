package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.domain.SessionStatus;
import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.entity.TimerSessionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimerSessionRepository extends JpaRepository<TimerSessionEntity, Long> {
    long countByModeAndStatusAndStartedAtBetween(TimerMode mode, SessionStatus status, LocalDateTime start, LocalDateTime end);

    List<TimerSessionEntity> findTop10ByOrderByStartedAtDesc();

    List<TimerSessionEntity> findByStartedAtBetweenOrderByStartedAtDesc(LocalDateTime start, LocalDateTime end);
}
