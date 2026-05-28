package cn.fanqie.pomodoro.dto;

import cn.fanqie.pomodoro.domain.PlanDraftStatus;
import cn.fanqie.pomodoro.domain.ScheduleSource;
import cn.fanqie.pomodoro.domain.ScheduleStatus;
import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.domain.TimerRunStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record SettingsResponse(
            int workMinutes,
            int shortBreakMinutes,
            int longBreakMinutes,
            int longBreakInterval,
            boolean notificationsEnabled,
            String alarmSound,
            boolean alarmRepeat,
            int alarmVolume,
            String theme
    ) {
    }

    public record SettingsUpdateRequest(
            @Min(1) @Max(180) int workMinutes,
            @Min(1) @Max(180) int shortBreakMinutes,
            @Min(1) @Max(180) int longBreakMinutes,
            @Min(1) @Max(12) int longBreakInterval,
            boolean notificationsEnabled,
            @NotBlank @Size(max = 128) String alarmSound,
            boolean alarmRepeat,
            @Min(0) @Max(100) int alarmVolume,
            @NotBlank @Size(max = 32) String theme
    ) {
    }

    public record TaskDto(
            Long id,
            String title,
            int estimatedPomodoros,
            int completedPomodoros,
            boolean done,
            boolean active,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 255) String title,
            @Min(0) @Max(100) int estimatedPomodoros
    ) {
    }

    public record UpdateTaskRequest(
            @Size(max = 255) String title,
            @Min(0) @Max(100) Integer estimatedPomodoros,
            @Min(0) @Max(100) Integer completedPomodoros,
            Boolean done,
            Boolean active,
            Integer sortOrder
    ) {
    }

    public record ReorderTasksRequest(@NotNull List<Long> taskIds) {
    }

    public record TimerStateDto(
            TimerMode mode,
            TimerRunStatus status,
            int totalSeconds,
            int remainingSeconds,
            Long sessionId,
            Long activeTaskId,
            String activeTaskTitle,
            int completedPomodorosToday,
            String hint
    ) {
    }

    public record TimerStartRequest(TimerMode mode) {
    }

    public record ScheduleItemDto(
            Long id,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            ScheduleStatus status,
            ScheduleSource source,
            Long taskId,
            String notes
    ) {
    }

    public record ScheduleUpsertRequest(
            @NotBlank @Size(max = 255) String title,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            ScheduleStatus status,
            ScheduleSource source,
            Long taskId,
            @Size(max = 2000) String notes
    ) {
    }

    public record StatsResponse(
            long completedPomodorosToday,
            long sessionsToday,
            long interruptionsToday,
            int unfinishedTasks,
            List<TimerSessionSummary> recentSessions
    ) {
    }

    public record DailyStatsDto(
            LocalDate date,
            long completedPomodoros,
            long sessions,
            long interruptions,
            int focusMinutes
    ) {
    }

    public record StatsTrendResponse(List<DailyStatsDto> days) {
    }

    public record CreateInterruptionRequest(
            @Size(max = 500) String note,
            Long taskId
    ) {
    }

    public record InterruptionDto(
            Long id,
            String note,
            Long taskId,
            String taskTitle,
            Long timerSessionId,
            LocalDateTime occurredAt
    ) {
    }

    public record TimerSessionSummary(
            Long id,
            TimerMode mode,
            String status,
            String taskTitle,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int elapsedSeconds
    ) {
    }

    public record AgentAdviceRequest(@Size(max = 2000) String question) {
    }

    public record ScheduleBlockDto(
            @NotBlank @Size(max = 255) String title,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            String notes,
            Long taskId
    ) {
    }

    public record AgentAdviceResponse(
            Long conversationId,
            String advice,
            String reasoningSummary,
            List<String> warnings
    ) {
    }

    public record AgentPlanResponse(
            Long draftId,
            String title,
            String advice,
            String reasoningSummary,
            List<ScheduleBlockDto> blocks,
            List<String> warnings
    ) {
    }

    public record AgentPlanDraftDto(
            Long draftId,
            PlanDraftStatus status,
            String title,
            String advice,
            String reasoningSummary,
            LocalDateTime createdAt,
            LocalDateTime appliedAt
    ) {
    }

    public record ScheduleBlockPreviewDto(
            int index,
            ScheduleBlockDto block,
            boolean conflict,
            String conflictMessage
    ) {
    }

    public record AgentPlanPreviewResponse(
            Long draftId,
            List<ScheduleBlockPreviewDto> blocks
    ) {
    }

    public record ApplyPlanRequest(List<Integer> blockIndexes) {
    }
}
