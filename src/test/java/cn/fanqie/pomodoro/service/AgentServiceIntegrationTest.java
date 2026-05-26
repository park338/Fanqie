package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.agent.LlmClient;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentAdviceRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.ApplyPlanRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.CreateTaskRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
import cn.fanqie.pomodoro.repository.AgentConversationRepository;
import cn.fanqie.pomodoro.repository.AgentPlanDraftRepository;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.ScheduleItemRepository;
import cn.fanqie.pomodoro.repository.TaskRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AgentServiceIntegrationTest {
    private static final AtomicInteger LLM_CALLS = new AtomicInteger();

    @Autowired
    AgentService agentService;

    @Autowired
    TaskService taskService;

    @Autowired
    TaskRepository tasks;

    @Autowired
    TimerSessionRepository timerSessions;

    @Autowired
    ScheduleItemRepository scheduleItems;

    @Autowired
    InterruptionRepository interruptions;

    @Autowired
    AgentConversationRepository conversations;

    @Autowired
    AgentPlanDraftRepository planDrafts;

    @BeforeEach
    void clean() {
        LLM_CALLS.set(0);
        interruptions.deleteAll();
        scheduleItems.deleteAll();
        timerSessions.deleteAll();
        planDrafts.deleteAll();
        conversations.deleteAll();
        tasks.deleteAll();
    }

    @Test
    void createsPlanDraftAndAppliesSelectedBlockToSchedule() {
        Long taskId = taskService.create(new CreateTaskRequest("实现小茄", 2)).id();

        AgentPlanResponse plan = agentService.plan(new AgentAdviceRequest("帮我排今天下午"));
        List<ScheduleItemDto> applied = agentService.applyPlan(plan.draftId(), new ApplyPlanRequest(List.of(0)));

        assertThat(LLM_CALLS.get()).isEqualTo(2);
        assertThat(plan.blocks()).hasSize(1);
        assertThat(plan.blocks().getFirst().taskId()).isEqualTo(taskId);
        assertThat(applied).singleElement()
                .satisfies(item -> {
                    assertThat(item.title()).isEqualTo("实现小茄");
                    assertThat(item.taskId()).isEqualTo(taskId);
                });
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlm(TaskRepository tasks) {
            return (systemPrompt, userPrompt) -> {
                int call = LLM_CALLS.incrementAndGet();
                if (call == 1) {
                    return """
                            {
                              "intent": "PLAN",
                              "polishedQuestion": "请把今天下午安排成可执行的番茄计划",
                              "timeRange": "今天下午",
                              "preferences": ["先做最重要任务"],
                              "constraints": ["保留短休息"],
                              "warnings": []
                            }
                            """;
                }
                Long taskId = tasks.findAll().getFirst().getId();
                return """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"title\\":\\"下午计划\\",\\"advice\\":\\"先完成小茄接口，再安排短休息。\\",\\"reasoningSummary\\":\\"根据当前任务生成一个可执行番茄块。\\",\\"blocks\\":[{\\"title\\":\\"实现小茄\\",\\"startAt\\":\\"2026-05-26T14:00:00\\",\\"endAt\\":\\"2026-05-26T14:25:00\\",\\"notes\\":\\"保持单任务推进\\",\\"taskId\\":%d}],\\"warnings\\":[]}"
                              }
                            }
                          ]
                        }
                        """.formatted(taskId);
            };
        }
    }
}
