package cn.fanqie.pomodoro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.fanqie.pomodoro.agent.LlmClient;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterHabitsDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPlanRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPlanResponse;
import cn.fanqie.pomodoro.repository.AgentConversationRepository;
import cn.fanqie.pomodoro.repository.AgentPlanDraftRepository;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentServiceTimeMasterTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-31T10:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void createsLongTaskCardFromLlmOutputUsingUserHabitsAndTaskDetails() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"title\\":\\"LLM 作品集周期规划\\",\\"summary\\":\\"基于早间深度工作，把作品集拆成启动、推进、交付三个阶段。\\",\\"totalDays\\":14,\\"dailyMinutes\\":90,\\"habits\\":{\\"energy\\":\\"morning\\",\\"focusStyle\\":\\"deep\\",\\"restPattern\\":\\"weekend-light\\",\\"reviewPreference\\":\\"weekly\\"},\\"phases\\":[{\\"id\\":\\"calibrate\\",\\"name\\":\\"启动校准\\",\\"startDate\\":\\"2026-06-01\\",\\"endDate\\":\\"2026-06-03\\",\\"objective\\":\\"先确定作品集边界和素材清单。\\",\\"dailyPlans\\":[{\\"date\\":\\"2026-06-01\\",\\"title\\":\\"筛选代表项目\\",\\"focusMinutes\\":90,\\"timeBlock\\":\\"08:30-10:00\\",\\"checklist\\":[\\"列出候选项目\\",\\"选出三个代表案例\\"],\\"scheduleTitle\\":\\"作品集：筛选代表项目\\",\\"scheduleNotes\\":\\"从高价值案例开始，降低后续返工。\\"}],\\"forecast\\":[{\\"date\\":\\"2026-06-01\\",\\"day\\":1,\\"value\\":7,\\"phaseId\\":\\"calibrate\\"}]}],\\"forecast\\":[{\\"date\\":\\"2026-06-01\\",\\"day\\":1,\\"value\\":7,\\"phaseId\\":\\"calibrate\\"}],\\"rationale\\":\\"先校准范围，再稳定推进，可以减少长任务的中途失焦。\\",\\"warnings\\":[]}"
                      }
                    }
                  ]
                }
                """);

        AgentService agent = new AgentService(
                llm,
                new ObjectMapper().findAndRegisterModules(),
                mock(TaskRepository.class),
                mock(InterruptionRepository.class),
                mock(ScheduleService.class),
                mock(TimerService.class),
                mock(StatsService.class),
                mock(AgentConversationRepository.class),
                mock(AgentPlanDraftRepository.class),
                clock
        );

        TimeMasterPlanResponse response = agent.timeMaster(new TimeMasterPlanRequest(
                "完成作品集",
                "整理项目、补文档、发布站点",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 14),
                90,
                new TimeMasterHabitsDto("morning", "deep", "weekend-light", "weekly")
        ));

        assertThat(response.title()).isEqualTo("LLM 作品集周期规划");
        assertThat(response.summary()).contains("早间深度工作");
        assertThat(response.totalDays()).isEqualTo(14);
        assertThat(response.phases()).singleElement()
                .satisfies(phase -> {
                    assertThat(phase.name()).isEqualTo("启动校准");
                    assertThat(phase.dailyPlans()).singleElement()
                            .satisfies(day -> {
                                assertThat(day.title()).isEqualTo("筛选代表项目");
                                assertThat(day.checklist()).contains("选出三个代表案例");
                                assertThat(day.timeBlock()).isEqualTo("08:30-10:00");
                            });
                });

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(llm).chat(org.mockito.ArgumentMatchers.contains("时间管理大师"), userPrompt.capture());
        assertThat(userPrompt.getValue()).contains("完成作品集", "整理项目、补文档、发布站点", "2026-06-14", "morning", "weekly");
    }
}
