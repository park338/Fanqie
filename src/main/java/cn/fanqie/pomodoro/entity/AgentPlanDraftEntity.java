package cn.fanqie.pomodoro.entity;

import cn.fanqie.pomodoro.domain.PlanDraftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_plan_drafts")
public class AgentPlanDraftEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanDraftStatus status;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String advice;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reasoningSummary;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String scheduleBlocksJson;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String rawResponse;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

    public Long getId() {
        return id;
    }

    public PlanDraftStatus getStatus() {
        return status;
    }

    public void setStatus(PlanDraftStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public String getReasoningSummary() {
        return reasoningSummary;
    }

    public void setReasoningSummary(String reasoningSummary) {
        this.reasoningSummary = reasoningSummary;
    }

    public String getScheduleBlocksJson() {
        return scheduleBlocksJson;
    }

    public void setScheduleBlocksJson(String scheduleBlocksJson) {
        this.scheduleBlocksJson = scheduleBlocksJson;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}
