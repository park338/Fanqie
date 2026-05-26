package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.dto.ApiDtos.CreateTaskRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TaskDto;
import cn.fanqie.pomodoro.dto.ApiDtos.UpdateTaskRequest;
import cn.fanqie.pomodoro.repository.AgentConversationRepository;
import cn.fanqie.pomodoro.repository.AgentPlanDraftRepository;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.ScheduleItemRepository;
import cn.fanqie.pomodoro.repository.TaskRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TaskServiceIntegrationTest {
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
        interruptions.deleteAll();
        scheduleItems.deleteAll();
        timerSessions.deleteAll();
        planDrafts.deleteAll();
        conversations.deleteAll();
        tasks.deleteAll();
    }

    @Test
    void createsFirstTaskAsActiveAndCanSwitchActiveTask() {
        TaskDto first = taskService.create(new CreateTaskRequest("写需求文档", 2));
        TaskDto second = taskService.create(new CreateTaskRequest("实现接口", 4));

        assertThat(first.active()).isTrue();
        assertThat(second.active()).isFalse();

        taskService.update(second.id(), new UpdateTaskRequest(null, null, null, null, true, null));
        List<TaskDto> all = taskService.list();

        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(TaskDto::active).singleElement()
                .extracting(TaskDto::title)
                .isEqualTo("实现接口");
    }
}
