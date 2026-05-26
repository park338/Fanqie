package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.entity.InterruptionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterruptionRepository extends JpaRepository<InterruptionEntity, Long> {
    List<InterruptionEntity> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime start, LocalDateTime end);
}
