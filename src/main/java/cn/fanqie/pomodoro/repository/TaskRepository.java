package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.entity.TaskEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findAllByOrderBySortOrderAscIdAsc();

    Optional<TaskEntity> findByActiveTrue();
}
