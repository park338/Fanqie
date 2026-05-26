package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.domain.TimerMode;
import cn.fanqie.pomodoro.domain.TimerRunStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.CreateTaskRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.SettingsUpdateRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStartRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStateDto;
import cn.fanqie.pomodoro.repository.AgentConversationRepository;
import cn.fanqie.pomodoro.repository.AgentPlanDraftRepository;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.ScheduleItemRepository;
import cn.fanqie.pomodoro.repository.TaskRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TimerServiceIntegrationTest {
    @Autowired
    TimerService timerService;

    @Autowired
    TaskService taskService;

    @Autowired
    SettingsService settingsService;

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
        timerService.reset();
        interruptions.deleteAll();
        scheduleItems.deleteAll();
        timerSessions.deleteAll();
        planDrafts.deleteAll();
        conversations.deleteAll();
        tasks.deleteAll();
        settingsService.update(new SettingsUpdateRequest(25, 5, 15, 4, false, "simple-notification", false, 50, "system"));
    }

    @Test
    void completingPomodoroIncrementsActiveTaskAndMovesToBreak() {
        taskService.create(new CreateTaskRequest("写测试", 1));

        TimerStateDto started = timerService.start(new TimerStartRequest(TimerMode.POMODORO));
        TimerStateDto completed = timerService.complete();

        assertThat(started.status()).isEqualTo(TimerRunStatus.PLAYING);
        assertThat(completed.mode()).isEqualTo(TimerMode.SHORT_BREAK);
        assertThat(completed.status()).isEqualTo(TimerRunStatus.NEW);
        assertThat(taskService.list().getFirst().completedPomodoros()).isEqualTo(1);
        assertThat(completed.completedPomodorosToday()).isGreaterThanOrEqualTo(1);
    }
}
