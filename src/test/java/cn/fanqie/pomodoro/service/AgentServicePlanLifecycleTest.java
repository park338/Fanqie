package cn.fanqie.pomodoro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.fanqie.pomodoro.agent.LlmClient;
import cn.fanqie.pomodoro.domain.PlanDraftStatus;
import cn.fanqie.pomodoro.domain.ScheduleSource;
import cn.fanqie.pomodoro.domain.ScheduleStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleBlockDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanDraftDto;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanPreviewResponse;
import cn.fanqie.pomodoro.entity.AgentPlanDraftEntity;
import cn.fanqie.pomodoro.repository.AgentConversationRepository;
import cn.fanqie.pomodoro.repository.AgentPlanDraftRepository;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AgentServicePlanLifecycleTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T08:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void applyingAlreadyAppliedDraftReturnsExistingScheduleItemsWithoutCreatingDuplicates() throws Exception {
        AgentPlanDraftRepository planDrafts = mock(AgentPlanDraftRepository.class);
        ScheduleService schedule = mock(ScheduleService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentPlanDraftEntity draft = new AgentPlanDraftEntity();
        ScheduleBlockDto block = new ScheduleBlockDto(
                "实现打断记录",
                LocalDateTime.of(2026, 5, 28, 16, 0),
                LocalDateTime.of(2026, 5, 28, 16, 25),
                "保持单任务推进",
                null
        );
        ScheduleItemDto created = new ScheduleItemDto(
                9L,
                block.title(),
                block.startAt(),
                block.endAt(),
                ScheduleStatus.PLANNED,
                ScheduleSource.AGENT,
                null,
                block.notes()
        );

        ReflectionTestUtils.setField(draft, "id", 5L);
        draft.setStatus(PlanDraftStatus.DRAFT);
        draft.setTitle("下午计划");
        draft.setAdvice("先做打断记录。");
        draft.setReasoningSummary("补齐统计输入。");
        draft.setScheduleBlocksJson(objectMapper.writeValueAsString(List.of(block)));
        draft.setRawResponse("{}");
        draft.setCreatedAt(LocalDateTime.now(clock));

        when(planDrafts.findById(5L)).thenReturn(Optional.of(draft));
        when(schedule.findConflictMessage(block)).thenReturn(null);
        when(schedule.createFromBlock(any(ScheduleBlockDto.class))).thenReturn(created);
        when(schedule.findByIds(List.of(9L))).thenReturn(List.of(created));

        AgentService agent = new AgentService(
                mock(LlmClient.class),
                objectMapper,
                mock(TaskRepository.class),
                mock(InterruptionRepository.class),
                schedule,
                mock(TimerService.class),
                mock(StatsService.class),
                mock(AgentConversationRepository.class),
                planDrafts,
                clock
        );

        List<ScheduleItemDto> firstApply = agent.applyPlan(5L, null);
        List<ScheduleItemDto> secondApply = agent.applyPlan(5L, null);

        assertThat(firstApply).extracting(ScheduleItemDto::id).containsExactly(9L);
        assertThat(secondApply).extracting(ScheduleItemDto::id).containsExactly(9L);
        verify(schedule, times(1)).createFromBlock(any(ScheduleBlockDto.class));
        assertThat(draft.getStatus()).isEqualTo(PlanDraftStatus.APPLIED);
    }

    @Test
    void rejectingDraftMarksItRejected() {
        AgentPlanDraftRepository planDrafts = mock(AgentPlanDraftRepository.class);
        AgentPlanDraftEntity draft = new AgentPlanDraftEntity();
        ReflectionTestUtils.setField(draft, "id", 6L);
        draft.setStatus(PlanDraftStatus.DRAFT);
        draft.setTitle("晚上计划");
        draft.setAdvice("收尾一下。");
        draft.setReasoningSummary("减少切换。");
        draft.setScheduleBlocksJson("[]");
        draft.setRawResponse("{}");
        draft.setCreatedAt(LocalDateTime.now(clock));
        when(planDrafts.findById(6L)).thenReturn(Optional.of(draft));

        AgentService agent = new AgentService(
                mock(LlmClient.class),
                new ObjectMapper().findAndRegisterModules(),
                mock(TaskRepository.class),
                mock(InterruptionRepository.class),
                mock(ScheduleService.class),
                mock(TimerService.class),
                mock(StatsService.class),
                mock(AgentConversationRepository.class),
                planDrafts,
                clock
        );

        AgentPlanDraftDto rejected = agent.rejectPlan(6L);

        assertThat(rejected.draftId()).isEqualTo(6L);
        assertThat(rejected.status()).isEqualTo(PlanDraftStatus.REJECTED);
        assertThat(draft.getStatus()).isEqualTo(PlanDraftStatus.REJECTED);
    }

    @Test
    void previewsPlanBlockConflictsBeforeApplying() throws Exception {
        AgentPlanDraftRepository planDrafts = mock(AgentPlanDraftRepository.class);
        ScheduleService schedule = mock(ScheduleService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ScheduleBlockDto block = new ScheduleBlockDto(
                "冲突任务",
                LocalDateTime.of(2026, 5, 28, 17, 0),
                LocalDateTime.of(2026, 5, 28, 17, 25),
                null,
                null
        );
        AgentPlanDraftEntity draft = new AgentPlanDraftEntity();
        ReflectionTestUtils.setField(draft, "id", 7L);
        draft.setStatus(PlanDraftStatus.DRAFT);
        draft.setTitle("冲突计划");
        draft.setAdvice("先检查冲突。");
        draft.setReasoningSummary("避免重复安排。");
        draft.setScheduleBlocksJson(objectMapper.writeValueAsString(List.of(block)));
        draft.setRawResponse("{}");
        draft.setCreatedAt(LocalDateTime.now(clock));

        when(planDrafts.findById(7L)).thenReturn(Optional.of(draft));
        when(schedule.findConflictMessage(block)).thenReturn("时间冲突：已存在安排「已有任务」。");

        AgentService agent = new AgentService(
                mock(LlmClient.class),
                objectMapper,
                mock(TaskRepository.class),
                mock(InterruptionRepository.class),
                schedule,
                mock(TimerService.class),
                mock(StatsService.class),
                mock(AgentConversationRepository.class),
                planDrafts,
                clock
        );

        AgentPlanPreviewResponse preview = agent.previewPlan(7L);

        assertThat(preview.draftId()).isEqualTo(7L);
        assertThat(preview.blocks()).singleElement().satisfies(item -> {
            assertThat(item.index()).isZero();
            assertThat(item.conflict()).isTrue();
            assertThat(item.conflictMessage()).contains("已有任务");
        });
    }
}
