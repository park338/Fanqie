package cn.fanqie.pomodoro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.fanqie.pomodoro.agent.LlmClient;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterDailyPlanDto;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentServiceTimeMasterTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-31T10:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void createsLongTaskCardFromLlmOutputUsingUserHabitsAndTaskDetails() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(any(), any())).thenReturn("""
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

        AgentService agent = newAgentService(llm);

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
        verify(llm).chat(contains("时间管理大师"), userPrompt.capture());
        assertThat(userPrompt.getValue()).contains("完成作品集", "整理项目、补文档、发布站点", "2026-06-14", "morning", "weekly");
    }

    @Test
    void diversifiesRepeatedLlmTimeBlocksAndTeachesLearningScience() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(any(), any())).thenReturn("""
                {
                  "title": "Java 学习计划",
                  "summary": "用项目练习推进 Java 基础。",
                  "totalDays": 4,
                  "dailyMinutes": 90,
                  "habits": {
                    "energy": "afternoon",
                    "focusStyle": "balanced",
                    "restPattern": "weekend-light",
                    "reviewPreference": "daily"
                  },
                  "phases": [
                    {
                      "id": "project",
                      "name": "项目实战",
                      "startDate": "2026-06-10",
                      "endDate": "2026-06-13",
                      "objective": "完成学生管理系统并复盘。",
                      "dailyPlans": [
                        {
                          "date": "2026-06-10",
                          "title": "项目设计：学生管理系统",
                          "focusMinutes": 90,
                          "timeBlock": "14:00-15:30",
                          "checklist": ["画出模块", "列出实体"],
                          "scheduleTitle": "Java：项目设计",
                          "scheduleNotes": "先明确边界。"
                        },
                        {
                          "date": "2026-06-11",
                          "title": "项目实现：核心功能编码",
                          "focusMinutes": 90,
                          "timeBlock": "14:00-15:30",
                          "checklist": ["实现增删改查", "记录卡点"],
                          "scheduleTitle": "Java：核心编码",
                          "scheduleNotes": "推进核心功能。"
                        },
                        {
                          "date": "2026-06-12",
                          "title": "项目完善与测试",
                          "focusMinutes": 90,
                          "timeBlock": "14:00-15:30",
                          "checklist": ["补充测试", "修正异常分支"],
                          "scheduleTitle": "Java：测试完善",
                          "scheduleNotes": "通过反馈改进质量。"
                        },
                        {
                          "date": "2026-06-13",
                          "title": "项目总结与最终复盘",
                          "focusMinutes": 90,
                          "timeBlock": "14:00-15:30",
                          "checklist": ["整理错题", "输出复盘"],
                          "scheduleTitle": "Java：最终复盘",
                          "scheduleNotes": "周末轻复盘。"
                        }
                      ],
                      "forecast": [
                        {"date": "2026-06-10", "day": 1, "value": 25, "phaseId": "project"},
                        {"date": "2026-06-11", "day": 2, "value": 50, "phaseId": "project"},
                        {"date": "2026-06-12", "day": 3, "value": 75, "phaseId": "project"},
                        {"date": "2026-06-13", "day": 4, "value": 100, "phaseId": "project"}
                      ]
                    }
                  ],
                  "forecast": [
                    {"date": "2026-06-10", "day": 1, "value": 25, "phaseId": "project"},
                    {"date": "2026-06-11", "day": 2, "value": 50, "phaseId": "project"},
                    {"date": "2026-06-12", "day": 3, "value": 75, "phaseId": "project"},
                    {"date": "2026-06-13", "day": 4, "value": 100, "phaseId": "project"}
                  ],
                  "rationale": "项目化推进并在最后复盘。",
                  "warnings": []
                }
                """);

        AgentService agent = newAgentService(llm);

        TimeMasterPlanResponse response = agent.timeMaster(new TimeMasterPlanRequest(
                "Java 学习",
                "学习集合、IO、数据库，并完成学生管理系统",
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 13),
                90,
                new TimeMasterHabitsDto("afternoon", "balanced", "weekend-light", "daily")
        ));

        List<String> timeBlocks = response.phases().getFirst().dailyPlans().stream()
                .map(TimeMasterDailyPlanDto::timeBlock)
                .toList();

        assertThat(timeBlocks).containsExactly("14:00-15:30", "15:00-16:30", "16:00-17:30", "15:30-17:00");
        assertThat(timeBlocks).doesNotHaveDuplicates();

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(llm).chat(systemPrompt.capture(), userPrompt.capture());
        assertThat(systemPrompt.getValue()).contains("主动回忆", "间隔重复", "交错练习", "刻意练习", "timeBlock");
        assertThat(userPrompt.getValue()).contains("学习方法参考", "检索练习", "不要把多天机械安排在同一个 timeBlock");
    }

    private AgentService newAgentService(LlmClient llm) {
        return new AgentService(
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
    }
}
