package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.entity.ScheduleItemEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItemEntity, Long> {
    List<ScheduleItemEntity> findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(LocalDateTime start, LocalDateTime end);

    List<ScheduleItemEntity> findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(LocalDateTime end, LocalDateTime start);
}
