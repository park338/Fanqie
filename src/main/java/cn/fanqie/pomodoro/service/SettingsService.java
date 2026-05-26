package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.dto.ApiDtos.SettingsResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.SettingsUpdateRequest;
import cn.fanqie.pomodoro.entity.SettingEntity;
import cn.fanqie.pomodoro.repository.SettingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
    private final SettingRepository settings;
    private final Clock clock;

    public SettingsService(SettingRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public SettingsResponse getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional
    public SettingEntity getOrCreate() {
        return settings.findById(SettingEntity.DEFAULT_ID)
                .orElseGet(() -> settings.save(SettingEntity.defaults(now())));
    }

    @Transactional
    public SettingsResponse update(SettingsUpdateRequest request) {
        SettingEntity entity = getOrCreate();
        entity.setWorkMinutes(request.workMinutes());
        entity.setShortBreakMinutes(request.shortBreakMinutes());
        entity.setLongBreakMinutes(request.longBreakMinutes());
        entity.setLongBreakInterval(request.longBreakInterval());
        entity.setNotificationsEnabled(request.notificationsEnabled());
        entity.setAlarmSound(request.alarmSound().trim());
        entity.setAlarmRepeat(request.alarmRepeat());
        entity.setAlarmVolume(request.alarmVolume());
        entity.setTheme(request.theme().trim());
        entity.touch(now());
        return toDto(entity);
    }

    public int durationSeconds(SettingEntity settings, cn.fanqie.pomodoro.domain.TimerMode mode) {
        return switch (mode) {
            case POMODORO -> settings.getWorkMinutes() * 60;
            case SHORT_BREAK -> settings.getShortBreakMinutes() * 60;
            case LONG_BREAK -> settings.getLongBreakMinutes() * 60;
        };
    }

    private SettingsResponse toDto(SettingEntity entity) {
        return new SettingsResponse(
                entity.getWorkMinutes(),
                entity.getShortBreakMinutes(),
                entity.getLongBreakMinutes(),
                entity.getLongBreakInterval(),
                entity.isNotificationsEnabled(),
                entity.getAlarmSound(),
                entity.isAlarmRepeat(),
                entity.getAlarmVolume(),
                entity.getTheme()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
