package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.domain.ScheduleSource;
import cn.fanqie.pomodoro.domain.ScheduleStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleUpsertRequest;
import cn.fanqie.pomodoro.entity.ScheduleItemEntity;
import cn.fanqie.pomodoro.repository.AgentConversationRepository;
import cn.fanqie.pomodoro.repository.AgentPlanDraftRepository;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.ScheduleItemRepository;
import cn.fanqie.pomodoro.repository.TaskRepository;
import cn.fanqie.pomodoro.repository.TimerSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ScheduleServiceIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    ScheduleItemRepository scheduleItems;

    @Autowired
    TimerSessionRepository timerSessions;

    @Autowired
    InterruptionRepository interruptions;

    @Autowired
    AgentConversationRepository conversations;

    @Autowired
    AgentPlanDraftRepository planDrafts;

    @Autowired
    TaskRepository tasks;

    @Autowired
    MutableTestClock clock;

    @BeforeEach
    void clean() {
        clock.set(LocalDateTime.of(2026, 5, 26, 15, 0));
        interruptions.deleteAll();
        scheduleItems.deleteAll();
        timerSessions.deleteAll();
        planDrafts.deleteAll();
        conversations.deleteAll();
        tasks.deleteAll();
    }

    @Test
    void advancesScheduleStatusFromPlannedToInProgressAndDone() {
        clock.set(LocalDateTime.of(2026, 5, 26, 15, 30));
        ScheduleItemDto created = scheduleService.create(request("Study", 15, 20, 15, 45));

        assertThat(created.status()).isEqualTo(ScheduleStatus.IN_PROGRESS);

        clock.set(LocalDateTime.of(2026, 5, 26, 15, 46));
        ScheduleItemDto refreshed = scheduleService.today().getFirst();

        assertThat(refreshed.status()).isEqualTo(ScheduleStatus.DONE);
        assertThat(scheduleItems.findById(refreshed.id())).get()
                .extracting(item -> item.getStatus())
                .isEqualTo(ScheduleStatus.DONE);
    }

    @Test
    void rejectsOverlappingScheduleItems() {
        scheduleService.create(request("Study", 15, 20, 15, 45));

        assertThatThrownBy(() -> scheduleService.create(request("Break", 15, 25, 15, 50)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("时间冲突");
    }

    @Test
    void marksExistingOverlapsAsSkippedWhenReadingTimeline() {
        clock.set(LocalDateTime.of(2026, 5, 26, 15, 30));
        saveLegacyItem("Study", 15, 20, 15, 45);
        saveLegacyItem("Break", 15, 25, 15, 50);

        assertThat(scheduleService.today())
                .extracting(ScheduleItemDto::status)
                .containsExactly(ScheduleStatus.IN_PROGRESS, ScheduleStatus.SKIPPED);
    }

    private ScheduleUpsertRequest request(String title, int startHour, int startMinute, int endHour, int endMinute) {
        return new ScheduleUpsertRequest(
                title,
                LocalDateTime.of(2026, 5, 26, startHour, startMinute),
                LocalDateTime.of(2026, 5, 26, endHour, endMinute),
                ScheduleStatus.PLANNED,
                ScheduleSource.USER,
                null,
                null
        );
    }

    private void saveLegacyItem(String title, int startHour, int startMinute, int endHour, int endMinute) {
        ScheduleItemEntity entity = new ScheduleItemEntity();
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 15, 0);
        entity.setTitle(title);
        entity.setStartAt(LocalDateTime.of(2026, 5, 26, startHour, startMinute));
        entity.setEndAt(LocalDateTime.of(2026, 5, 26, endHour, endMinute));
        entity.setStatus(ScheduleStatus.PLANNED);
        entity.setSource(ScheduleSource.USER);
        entity.setNotes(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        scheduleItems.save(entity);
    }

    static class MutableTestClock extends Clock {
        private Instant instant = LocalDateTime.of(2026, 5, 26, 15, 0).atZone(ZONE).toInstant();

        void set(LocalDateTime value) {
            instant = value.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableTestClock mutableTestClock() {
            return new MutableTestClock();
        }
    }
}
