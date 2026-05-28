package cn.fanqie.pomodoro.entity;

import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.domain.TimerRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "timer_states")
public class TimerStateEntity {
    public static final String DEFAULT_ID = "default";

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimerMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimerRunStatus status;

    @Column(nullable = false)
    private int totalSeconds;

    @Column(nullable = false)
    private int elapsedSeconds;

    private Long sessionId;

    private LocalDateTime lastStartedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static TimerStateEntity initial(TimerMode mode, int totalSeconds, LocalDateTime now) {
        TimerStateEntity entity = new TimerStateEntity();
        entity.id = DEFAULT_ID;
        entity.mode = mode;
        entity.status = TimerRunStatus.NEW;
        entity.totalSeconds = totalSeconds;
        entity.elapsedSeconds = 0;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public String getId() {
        return id;
    }

    public TimerMode getMode() {
        return mode;
    }

    public void setMode(TimerMode mode) {
        this.mode = mode;
    }

    public TimerRunStatus getStatus() {
        return status;
    }

    public void setStatus(TimerRunStatus status) {
        this.status = status;
    }

    public int getTotalSeconds() {
        return totalSeconds;
    }

    public void setTotalSeconds(int totalSeconds) {
        this.totalSeconds = totalSeconds;
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void setElapsedSeconds(int elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(LocalDateTime lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
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
