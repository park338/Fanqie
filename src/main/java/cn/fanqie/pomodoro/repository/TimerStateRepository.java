package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.entity.TimerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimerStateRepository extends JpaRepository<TimerStateEntity, String> {
}
