package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.entity.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<SettingEntity, String> {
}
