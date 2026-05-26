package cn.fanqie.pomodoro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "settings")
public class SettingEntity {
    public static final String DEFAULT_ID = "default";

    @Id
    private String id;

    @Column(nullable = false)
    private int workMinutes;

    @Column(nullable = false)
    private int shortBreakMinutes;

    @Column(nullable = false)
    private int longBreakMinutes;

    @Column(nullable = false)
    private int longBreakInterval;

    @Column(nullable = false)
    private boolean notificationsEnabled;

    @Column(nullable = false)
    private String alarmSound;

    @Column(nullable = false)
    private boolean alarmRepeat;

    @Column(nullable = false)
    private int alarmVolume;

    @Column(nullable = false)
    private String theme;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static SettingEntity defaults(LocalDateTime now) {
        SettingEntity entity = new SettingEntity();
        entity.id = DEFAULT_ID;
        entity.workMinutes = 25;
        entity.shortBreakMinutes = 5;
        entity.longBreakMinutes = 15;
        entity.longBreakInterval = 4;
        entity.notificationsEnabled = false;
        entity.alarmSound = "simple-notification";
        entity.alarmRepeat = false;
        entity.alarmVolume = 50;
        entity.theme = "system";
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public String getId() {
        return id;
    }

    public int getWorkMinutes() {
        return workMinutes;
    }

    public void setWorkMinutes(int workMinutes) {
        this.workMinutes = workMinutes;
    }

    public int getShortBreakMinutes() {
        return shortBreakMinutes;
    }

    public void setShortBreakMinutes(int shortBreakMinutes) {
        this.shortBreakMinutes = shortBreakMinutes;
    }

    public int getLongBreakMinutes() {
        return longBreakMinutes;
    }

    public void setLongBreakMinutes(int longBreakMinutes) {
        this.longBreakMinutes = longBreakMinutes;
    }

    public int getLongBreakInterval() {
        return longBreakInterval;
    }

    public void setLongBreakInterval(int longBreakInterval) {
        this.longBreakInterval = longBreakInterval;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getAlarmSound() {
        return alarmSound;
    }

    public void setAlarmSound(String alarmSound) {
        this.alarmSound = alarmSound;
    }

    public boolean isAlarmRepeat() {
        return alarmRepeat;
    }

    public void setAlarmRepeat(boolean alarmRepeat) {
        this.alarmRepeat = alarmRepeat;
    }

    public int getAlarmVolume() {
        return alarmVolume;
    }

    public void setAlarmVolume(int alarmVolume) {
        this.alarmVolume = alarmVolume;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }
}
