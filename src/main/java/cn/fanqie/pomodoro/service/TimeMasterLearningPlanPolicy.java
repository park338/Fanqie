package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterDailyPlanDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterHabitsDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPlanRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TimeMasterLearningPlanPolicy {
    static final String SYSTEM_RULES = """
            学习科学规则：
            - dailyPlans 必须体现主动回忆/检索练习、间隔重复、交错练习、刻意练习与反馈、渐进难度、项目化应用和复盘。
            - 对新内容先设计问题或小测，再安排阅读、编码、输出或讲解；不要把计划写成连续被动阅读。
            - timeBlock 必须符合用户 energy，但不要机械重复；同一阶段超过 2 天时至少使用 2 个合理时段，超过 5 天时至少使用 3 个合理时段。
            - restPattern 为 weekend-light 或 weekday-only 时，周末应降低强度或安排复盘/轻任务；不得把高强度任务连续堆满。
            - focusStyle 为 fragmented 时，用 checklist 描述 25-50 分钟微小节；focusStyle 为 deep 时保留 90-120 分钟主攻块并安排休息。
            """;

    String contextGuidance(TimeMasterPlanRequest request) {
        return """
                学习方法参考:
                - 主动回忆/检索练习: 每天都安排“不看资料先答题、默写、复述、讲给别人听或写测试用例”的动作。
                - 间隔重复: 把复习放在第 1/3/7 天或阶段切换日，不要只在最后一天复习。
                - 交错练习: 对知识型任务混合新知识、旧知识和应用题；对项目型任务混合设计、编码、测试和复盘。
                - 刻意练习: 每天只选择一个最弱环节攻克，要求 checklist 里有反馈来源，如测试、对照答案、运行结果或自我解释。
                - 渐进难度: 前期降低启动阻力，中期增加难度，末期留出整合、纠错和交付缓冲。
                - 项目化应用: 能做成作品、小 demo、错题集、复盘文档时，优先安排可见产出。

                排程规则:
                - 当前每日投入 %d 分钟，timeBlock 表示当天主学习窗口；如果投入时间较长，请在 checklist 或 scheduleNotes 写明拆成多个小节和休息。
                - energy=morning 可使用 08:30-10:00、09:00-10:30、10:00-11:30 等主时段。
                - energy=afternoon 可使用 14:00-15:30、15:00-16:30、16:00-17:30 等主时段。
                - energy=evening 可使用 19:30-21:00、20:00-21:30、20:30-22:00 等主时段。
                - 不要把多天机械安排在同一个 timeBlock，例如不能连续都写 14:00-15:30。
                """.formatted(request.dailyMinutes());
    }

    List<TimeMasterDailyPlanDto> diversifyRepeatedTimeBlocks(
            List<TimeMasterDailyPlanDto> plans,
            TimeMasterHabitsDto habits
    ) {
        if (!needsDiversification(plans)) {
            return plans;
        }

        List<TimeMasterDailyPlanDto> diversified = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            TimeMasterDailyPlanDto plan = plans.get(index);
            diversified.add(new TimeMasterDailyPlanDto(
                    plan.date(),
                    plan.title(),
                    plan.focusMinutes(),
                    suggestedTimeBlock(plan, habits, index),
                    plan.checklist(),
                    plan.scheduleTitle(),
                    plan.scheduleNotes()
            ));
        }
        return diversified;
    }

    private boolean needsDiversification(List<TimeMasterDailyPlanDto> plans) {
        if (plans == null || plans.size() < 2) {
            return false;
        }

        int distinct = (int) plans.stream().map(TimeMasterDailyPlanDto::timeBlock).distinct().count();
        if (distinct <= 1) {
            return true;
        }

        int streak = 1;
        String previous = plans.getFirst().timeBlock();
        for (int index = 1; index < plans.size(); index++) {
            String current = plans.get(index).timeBlock();
            if (current.equals(previous)) {
                streak += 1;
                if (streak >= 3) {
                    return true;
                }
            } else {
                streak = 1;
                previous = current;
            }
        }
        return false;
    }

    private String suggestedTimeBlock(TimeMasterDailyPlanDto plan, TimeMasterHabitsDto habits, int index) {
        LocalTime start = suggestedStartTime(plan.date(), habits, index);
        LocalTime end = start.plusMinutes(blockMinutes(plan.focusMinutes(), habits));
        return "%02d:%02d-%02d:%02d".formatted(
                start.getHour(),
                start.getMinute(),
                end.getHour(),
                end.getMinute()
        );
    }

    private LocalTime suggestedStartTime(LocalDate date, TimeMasterHabitsDto habits, int index) {
        if (isLightWeekend(date, habits)) {
            List<LocalTime> weekendSlots = weekendStartSlots(habits.energy());
            return weekendSlots.get(index % weekendSlots.size());
        }
        List<LocalTime> slots = startSlots(habits.energy(), habits.focusStyle());
        return slots.get(index % slots.size());
    }

    private List<LocalTime> startSlots(String energy, String focusStyle) {
        String normalizedEnergy = normalize(energy);
        String normalizedFocus = normalize(focusStyle);
        if (normalizedEnergy.contains("afternoon")) {
            if (normalizedFocus.contains("fragmented")) {
                return List.of(LocalTime.of(13, 30), LocalTime.of(15, 0), LocalTime.of(16, 30), LocalTime.of(14, 30));
            }
            return List.of(LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(13, 30));
        }
        if (normalizedEnergy.contains("evening")) {
            if (normalizedFocus.contains("fragmented")) {
                return List.of(LocalTime.of(19, 0), LocalTime.of(20, 0), LocalTime.of(21, 0), LocalTime.of(18, 30));
            }
            return List.of(LocalTime.of(19, 30), LocalTime.of(20, 0), LocalTime.of(20, 30), LocalTime.of(18, 30));
        }
        if (normalizedFocus.contains("fragmented")) {
            return List.of(LocalTime.of(8, 0), LocalTime.of(9, 30), LocalTime.of(11, 0), LocalTime.of(7, 30));
        }
        return List.of(LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(7, 30));
    }

    private List<LocalTime> weekendStartSlots(String energy) {
        String normalizedEnergy = normalize(energy);
        if (normalizedEnergy.contains("afternoon")) {
            return List.of(LocalTime.of(15, 30), LocalTime.of(14, 30), LocalTime.of(16, 30));
        }
        if (normalizedEnergy.contains("evening")) {
            return List.of(LocalTime.of(20, 30), LocalTime.of(19, 30), LocalTime.of(21, 0));
        }
        return List.of(LocalTime.of(10, 0), LocalTime.of(9, 30), LocalTime.of(10, 30));
    }

    private boolean isLightWeekend(LocalDate date, TimeMasterHabitsDto habits) {
        if (date == null) {
            return false;
        }
        boolean weekend = date.getDayOfWeek().getValue() >= 6;
        String restPattern = normalize(habits.restPattern());
        return weekend && (restPattern.contains("weekend-light") || restPattern.contains("weekday-only"));
    }

    private int blockMinutes(int focusMinutes, TimeMasterHabitsDto habits) {
        int maxBlock = normalize(habits.focusStyle()).contains("fragmented") ? 60 : 120;
        return clamp(focusMinutes, 25, maxBlock);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
