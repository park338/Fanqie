package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.agent.LlmClient;
import cn.fanqie.pomodoro.domain.AgentKind;
import cn.fanqie.pomodoro.domain.PlanDraftStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentAdviceRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentAdviceResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanDraftDto;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanPreviewResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.ApplyPlanRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleBlockDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleBlockPreviewDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterDailyPlanDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterForecastPointDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterHabitsDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPhaseDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPlanRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPlanResponse;
import cn.fanqie.pomodoro.entity.AgentConversationEntity;
import cn.fanqie.pomodoro.entity.AgentPlanDraftEntity;
import cn.fanqie.pomodoro.repository.AgentConversationRepository;
import cn.fanqie.pomodoro.repository.AgentPlanDraftRepository;
import cn.fanqie.pomodoro.repository.InterruptionRepository;
import cn.fanqie.pomodoro.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {
    private static final String REFINEMENT_PROMPT = """
            你是 Fanqie 番茄钟应用的需求理解助手。你的任务不是回答用户，而是把用户输入润色成后端可校验的字段。
            只能返回 JSON 对象，字段为 intent、polishedQuestion、timeRange、preferences、constraints、warnings。
            intent 只能是 ADVICE 或 PLAN；preferences、constraints、warnings 都是字符串数组。
            不要编造任务完成情况，不要生成具体日程块。
            """;

    private static final String FINAL_PROMPT = """
            你是番茄钟应用 Fanqie 的时间管理智能体，名字叫“小茄”。
            你必须用简体中文回答，语气温和、具体、可执行。
            用户输入已经先经过一次 LLM 润色，再由后端做字段校验和上下文拼接；你只基于最终上下文做回应。
            只能返回 JSON 对象，字段为 title、advice、reasoningSummary、blocks、warnings。
            blocks 是今日计划块数组，每项包含 title、startAt、endAt、notes、taskId；时间必须是 ISO-8601 本地时间。
            不要编造已经完成的工作；如果信息不足，请给保守建议。
            """;

    private static final String TIME_MASTER_PROMPT = """
            你是 Fanqie 的“时间管理大师”长期任务规划 agent。
            你的任务是根据用户的生活习惯、任务内容、开始日期、结束日期和每日可投入时间，生成可执行的长期任务资料卡。
            必须用简体中文，只能返回 JSON 对象，不要返回 Markdown。
            JSON 字段必须为 title、summary、totalDays、dailyMinutes、habits、phases、forecast、rationale、warnings。
            habits 必须原样返回 energy、focusStyle、restPattern、reviewPreference。
            phases 是阶段数组，每项必须包含 id、name、startDate、endDate、objective、dailyPlans、forecast。
            dailyPlans 是每日计划数组，每项必须包含 date、title、focusMinutes、timeBlock、checklist、scheduleTitle、scheduleNotes。
            timeBlock 必须使用 HH:mm-HH:mm；date、startDate、endDate 必须使用 YYYY-MM-DD。
            forecast 是线性提升预测点数组，每项包含 date、day、value、phaseId；value 使用 0 到 100 的整数，最后一个点必须是 100。
            rationale 要解释为什么这样拆阶段、安排强度和复盘节奏。
            不要声称用户已经完成某项工作，不要编造用户没有提供的固定日程。
            """;

    private final LlmClient llm;
    private final ObjectMapper objectMapper;
    private final TaskRepository tasks;
    private final InterruptionRepository interruptions;
    private final ScheduleService scheduleService;
    private final TimerService timerService;
    private final StatsService statsService;
    private final AgentConversationRepository conversations;
    private final AgentPlanDraftRepository planDrafts;
    private final Clock clock;

    public AgentService(
            LlmClient llm,
            ObjectMapper objectMapper,
            TaskRepository tasks,
            InterruptionRepository interruptions,
            ScheduleService scheduleService,
            TimerService timerService,
            StatsService statsService,
            AgentConversationRepository conversations,
            AgentPlanDraftRepository planDrafts,
            Clock clock
    ) {
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.tasks = tasks;
        this.interruptions = interruptions;
        this.scheduleService = scheduleService;
        this.timerService = timerService;
        this.statsService = statsService;
        this.conversations = conversations;
        this.planDrafts = planDrafts;
        this.clock = clock;
    }

    @Transactional
    public AgentAdviceResponse advice(AgentAdviceRequest request) {
        String question = request.question() == null || request.question().isBlank()
                ? "请根据我当前的任务、番茄记录和时间安排，给我一条接下来最该做什么的建议。"
                : request.question().trim();
        RefinedAgentRequest refined = refineRequest("ADVICE", question);
        String raw = llm.chat(FINAL_PROMPT, contextPrompt("ADVICE", question, refined));
        ParsedAgentResponse parsed = parse(raw);
        AgentConversationEntity conversation = saveConversation(AgentKind.ADVICE, question, parsed.advice(), raw);
        return new AgentAdviceResponse(conversation.getId(), parsed.advice(), parsed.reasoningSummary(), mergeWarnings(refined.warnings(), parsed.warnings()));
    }

    @Transactional
    public AgentPlanResponse plan(AgentAdviceRequest request) {
        String question = request.question() == null || request.question().isBlank()
                ? "请为我生成今天剩余时间的番茄钟计划。"
                : request.question().trim();
        RefinedAgentRequest refined = refineRequest("PLAN", question);
        String raw = llm.chat(FINAL_PROMPT, contextPrompt("PLAN", question, refined));
        ParsedAgentResponse parsed = parse(raw);
        AgentPlanDraftEntity draft = new AgentPlanDraftEntity();
        draft.setStatus(PlanDraftStatus.DRAFT);
        draft.setTitle(parsed.title());
        draft.setAdvice(parsed.advice());
        draft.setReasoningSummary(parsed.reasoningSummary());
        draft.setScheduleBlocksJson(writeJson(parsed.blocks()));
        draft.setRawResponse(raw);
        draft.setCreatedAt(now());
        draft = planDrafts.save(draft);
        saveConversation(AgentKind.PLAN, question, parsed.advice(), raw);
        return new AgentPlanResponse(draft.getId(), parsed.title(), parsed.advice(), parsed.reasoningSummary(), parsed.blocks(), mergeWarnings(refined.warnings(), parsed.warnings()));
    }

    @Transactional
    public TimeMasterPlanResponse timeMaster(TimeMasterPlanRequest request) {
        rejectInvalidTimeMasterRequest(request);
        String raw = llm.chat(TIME_MASTER_PROMPT, timeMasterContextPrompt(request));
        TimeMasterPlanResponse response = parseTimeMaster(raw, request);
        saveConversation(AgentKind.TIME_MASTER, timeMasterUserMessage(request), response.summary(), raw);
        return response;
    }

    @Transactional
    public List<ScheduleItemDto> applyPlan(Long draftId, ApplyPlanRequest request) {
        AgentPlanDraftEntity draft = planDrafts.findById(draftId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "计划草稿不存在"));
        if (draft.getStatus() == PlanDraftStatus.APPLIED) {
            return scheduleService.findByIds(readAppliedScheduleItemIds(draft));
        }
        if (draft.getStatus() != PlanDraftStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "计划草稿已经处理过");
        }
        List<ScheduleBlockDto> blocks = readBlocks(draft.getScheduleBlocksJson());
        List<Integer> indexes = request == null || request.blockIndexes() == null || request.blockIndexes().isEmpty()
                ? allIndexes(blocks)
                : request.blockIndexes();
        List<ScheduleBlockDto> selected = new ArrayList<>();
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= blocks.size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "计划块索引无效");
            }
            ScheduleBlockDto block = blocks.get(index);
            String conflict = scheduleService.findConflictMessage(block);
            if (conflict != null) {
                throw new ApiException(HttpStatus.CONFLICT, conflict);
            }
            selected.add(block);
        }
        rejectInternalBlockConflicts(selected);
        List<ScheduleItemDto> created = new ArrayList<>();
        for (ScheduleBlockDto block : selected) {
            created.add(scheduleService.createFromBlock(block));
        }
        draft.setStatus(PlanDraftStatus.APPLIED);
        draft.setAppliedScheduleItemIdsJson(writeJson(created.stream().map(ScheduleItemDto::id).toList()));
        draft.setAppliedAt(now());
        return created;
    }

    @Transactional
    public AgentPlanDraftDto rejectPlan(Long draftId) {
        AgentPlanDraftEntity draft = planDrafts.findById(draftId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "计划草稿不存在"));
        if (draft.getStatus() == PlanDraftStatus.DRAFT) {
            draft.setStatus(PlanDraftStatus.REJECTED);
        }
        return toDraftDto(draft);
    }

    @Transactional
    public AgentPlanPreviewResponse previewPlan(Long draftId) {
        AgentPlanDraftEntity draft = planDrafts.findById(draftId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "计划草稿不存在"));
        List<ScheduleBlockDto> blocks = readBlocks(draft.getScheduleBlocksJson());
        List<ScheduleBlockPreviewDto> previews = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            ScheduleBlockDto block = blocks.get(i);
            String conflict = scheduleService.findConflictMessage(block);
            previews.add(new ScheduleBlockPreviewDto(i, block, conflict != null, conflict));
        }
        return new AgentPlanPreviewResponse(draft.getId(), previews);
    }

    @Transactional(readOnly = true)
    public List<AgentPlanDraftDto> recentPlans() {
        return planDrafts.findTop5ByOrderByCreatedAtDesc().stream()
                .map(this::toDraftDto)
                .toList();
    }

    private RefinedAgentRequest refineRequest(String expectedIntent, String question) {
        String raw = llm.chat(REFINEMENT_PROMPT, """
                接口意图: %s
                当前时间: %s
                用户原始输入: %s
                """.formatted(expectedIntent, now(), question));
        try {
            JsonNode node = objectMapper.readTree(unwrapOpenAiResponse(raw));
            String intent = text(node, "intent", expectedIntent).toUpperCase();
            if (!"ADVICE".equals(intent) && !"PLAN".equals(intent)) {
                intent = expectedIntent;
            }
            if (!expectedIntent.equals(intent)) {
                intent = expectedIntent;
            }
            String polishedQuestion = cleanText(text(node, "polishedQuestion", question), question, 1000);
            String timeRange = cleanText(text(node, "timeRange", "今天剩余时间"), "今天剩余时间", 120);
            List<String> preferences = readLimitedStringList(node.get("preferences"));
            List<String> constraints = readLimitedStringList(node.get("constraints"));
            List<String> warnings = readLimitedStringList(node.get("warnings"));
            return new RefinedAgentRequest(intent, polishedQuestion, timeRange, preferences, constraints, warnings);
        } catch (Exception ex) {
            return new RefinedAgentRequest(
                    expectedIntent,
                    question,
                    "今天剩余时间",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    List.of("输入润色失败，已使用原始需求")
            );
        }
    }

    private String contextPrompt(String mode, String question, RefinedAgentRequest refined) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        return """
                任务类型: %s
                当前时间: %s
                用户原始输入: %s
                后端校验后的润色字段:
                - intent: %s
                - polishedQuestion: %s
                - timeRange: %s
                - preferences: %s
                - constraints: %s
                当前计时器: %s
                今日统计: %s
                未完成任务: %s
                时间安排: %s
                今日干扰记录: %s
                """.formatted(
                mode,
                now(),
                question,
                refined.intent(),
                refined.polishedQuestion(),
                refined.timeRange(),
                writeJson(refined.preferences()),
                writeJson(refined.constraints()),
                writeJson(timerService.current()),
                writeJson(statsService.today()),
                writeJson(tasks.findAllByOrderBySortOrderAscIdAsc().stream().filter(task -> !task.isDone()).toList()),
                writeJson(scheduleService.listForDate(today)),
                writeJson(interruptions.findByOccurredAtBetweenOrderByOccurredAtDesc(dayStart, dayEnd))
        );
    }

    private void rejectInvalidTimeMasterRequest(TimeMasterPlanRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        long totalDays = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (totalDays > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "时间管理大师暂时支持 180 天以内的长期任务");
        }
    }

    private String timeMasterContextPrompt(TimeMasterPlanRequest request) {
        return """
                当前时间: %s
                任务名称: %s
                任务内容: %s
                开始日期: %s
                结束日期: %s
                总天数: %d
                每日可投入分钟: %d
                用户习惯:
                - energy: %s
                - focusStyle: %s
                - restPattern: %s
                - reviewPreference: %s

                请基于以上输入给出阶段拆分、每日计划、线性提升预测和规划原因。
                每个 dailyPlans 项都要能直接加入首页时间安排：scheduleTitle 要像日程标题，scheduleNotes 要说明当天动作和安排理由。
                """.formatted(
                now(),
                request.taskTitle().trim(),
                request.taskContent().trim(),
                request.startDate(),
                request.endDate(),
                totalTimeMasterDays(request),
                request.dailyMinutes(),
                request.habits().energy(),
                request.habits().focusStyle(),
                request.habits().restPattern(),
                request.habits().reviewPreference()
        );
    }

    private String timeMasterUserMessage(TimeMasterPlanRequest request) {
        return "%s｜%s 到 %s｜每日 %d 分钟".formatted(
                request.taskTitle().trim(),
                request.startDate(),
                request.endDate(),
                request.dailyMinutes()
        );
    }

    private int totalTimeMasterDays(TimeMasterPlanRequest request) {
        return (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
    }

    private AgentConversationEntity saveConversation(AgentKind kind, String userMessage, String summary, String raw) {
        AgentConversationEntity entity = new AgentConversationEntity();
        entity.setKind(kind);
        entity.setUserMessage(userMessage);
        entity.setResponseSummary(summary);
        entity.setRawResponse(raw);
        entity.setCreatedAt(now());
        return conversations.save(entity);
    }

    private AgentPlanDraftDto toDraftDto(AgentPlanDraftEntity draft) {
        return new AgentPlanDraftDto(
                draft.getId(),
                draft.getStatus(),
                draft.getTitle(),
                draft.getAdvice(),
                draft.getReasoningSummary(),
                draft.getCreatedAt(),
                draft.getAppliedAt()
        );
    }

    private ParsedAgentResponse parse(String raw) {
        try {
            String json = unwrapOpenAiResponse(raw);
            JsonNode node = objectMapper.readTree(json);
            String title = text(node, "title", "小茄建议");
            String advice = text(node, "advice", "先选择一个最小任务，完成一个番茄后再调整计划。");
            String reasoning = text(node, "reasoningSummary", "基于当前任务、时间安排和番茄完成情况给出。");
            List<String> warnings = readStringList(node.get("warnings"));
            List<ScheduleBlockDto> blocks = readBlocksNode(node.get("blocks"));
            return new ParsedAgentResponse(title, advice, reasoning, blocks, warnings);
        } catch (Exception ex) {
            return new ParsedAgentResponse(
                    "小茄建议",
                    "模型返回格式不稳定。小茄建议先挑一个当前最重要的任务，做一个 25 分钟番茄，再根据完成情况调整时间安排。",
                    "AI JSON 解析失败，使用本地降级建议。",
                    Collections.emptyList(),
                    List.of("AI 返回内容无法解析为计划 JSON")
            );
        }
    }

    private TimeMasterPlanResponse parseTimeMaster(String raw, TimeMasterPlanRequest request) {
        try {
            String json = unwrapOpenAiResponse(raw);
            JsonNode node = objectMapper.readTree(json);
            List<TimeMasterPhaseDto> phases = readTimeMasterPhasesNode(node.get("phases"));
            if (phases.isEmpty()) {
                throw new IllegalArgumentException("missing phases");
            }
            List<TimeMasterForecastPointDto> forecast = readTimeMasterForecastNode(node.get("forecast"));
            if (forecast.isEmpty()) {
                forecast = phases.stream().flatMap(phase -> phase.forecast().stream()).toList();
            }
            return new TimeMasterPlanResponse(
                    cleanText(text(node, "title", request.taskTitle()), request.taskTitle(), 255),
                    cleanText(text(node, "summary", request.taskContent()), request.taskContent(), 2000),
                    intValue(node, "totalDays", totalTimeMasterDays(request)),
                    intValue(node, "dailyMinutes", request.dailyMinutes()),
                    readTimeMasterHabits(node.get("habits"), request.habits()),
                    phases,
                    forecast,
                    cleanText(text(node, "rationale", "根据任务周期、每日投入和习惯偏好拆分阶段，并保留复盘与交付缓冲。"), "根据任务周期、每日投入和习惯偏好拆分阶段，并保留复盘与交付缓冲。", 2000),
                    readLimitedStringList(node.get("warnings"))
            );
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "时间管理大师返回内容无法解析，请稍后重试");
        }
    }

    private TimeMasterHabitsDto readTimeMasterHabits(JsonNode node, TimeMasterHabitsDto fallback) {
        if (node == null || !node.isObject()) {
            return fallback;
        }
        return new TimeMasterHabitsDto(
                cleanText(text(node, "energy", fallback.energy()), fallback.energy(), 32),
                cleanText(text(node, "focusStyle", fallback.focusStyle()), fallback.focusStyle(), 32),
                cleanText(text(node, "restPattern", fallback.restPattern()), fallback.restPattern(), 32),
                cleanText(text(node, "reviewPreference", fallback.reviewPreference()), fallback.reviewPreference(), 32)
        );
    }

    private List<TimeMasterPhaseDto> readTimeMasterPhasesNode(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TimeMasterPhaseDto> phases = new ArrayList<>();
        for (JsonNode item : node) {
            try {
                TimeMasterPhaseDto phase = objectMapper.treeToValue(item, TimeMasterPhaseDto.class);
                if (phase.endDate().isBefore(phase.startDate())) {
                    continue;
                }
                phases.add(new TimeMasterPhaseDto(
                        cleanText(phase.id(), "phase-" + (phases.size() + 1), 64),
                        cleanText(phase.name(), "阶段 " + (phases.size() + 1), 80),
                        phase.startDate(),
                        phase.endDate(),
                        cleanText(phase.objective(), "推进当前阶段目标。", 500),
                        sanitizeDailyPlans(phase.dailyPlans()),
                        sanitizeForecast(phase.forecast())
                ));
            } catch (Exception ignored) {
                // Skip malformed phases while preserving any valid LLM-generated parts.
            }
        }
        return phases;
    }

    private List<TimeMasterDailyPlanDto> sanitizeDailyPlans(List<TimeMasterDailyPlanDto> plans) {
        if (plans == null) {
            return Collections.emptyList();
        }
        List<TimeMasterDailyPlanDto> sanitized = new ArrayList<>();
        for (TimeMasterDailyPlanDto plan : plans) {
            if (plan == null || plan.date() == null) {
                continue;
            }
            sanitized.add(new TimeMasterDailyPlanDto(
                    plan.date(),
                    cleanText(plan.title(), "每日推进", 255),
                    clamp(plan.focusMinutes(), 1, 360),
                    cleanText(plan.timeBlock(), "08:30-09:30", 32),
                    cleanStringList(plan.checklist(), 8, 160),
                    cleanText(plan.scheduleTitle(), plan.title(), 255),
                    cleanText(plan.scheduleNotes(), "来自时间管理大师的长期任务安排。", 2000)
            ));
        }
        return sanitized;
    }

    private List<TimeMasterForecastPointDto> readTimeMasterForecastNode(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TimeMasterForecastPointDto> points = new ArrayList<>();
        for (JsonNode item : node) {
            try {
                TimeMasterForecastPointDto point = objectMapper.treeToValue(item, TimeMasterForecastPointDto.class);
                if (point.date() != null && point.phaseId() != null && !point.phaseId().isBlank()) {
                    points.add(new TimeMasterForecastPointDto(
                            point.date(),
                            Math.max(1, point.day()),
                            clamp(point.value(), 0, 100),
                            cleanText(point.phaseId(), "phase", 64)
                    ));
                }
            } catch (Exception ignored) {
                // Skip malformed forecast points while preserving the rest.
            }
        }
        return points;
    }

    private List<TimeMasterForecastPointDto> sanitizeForecast(List<TimeMasterForecastPointDto> points) {
        if (points == null) {
            return Collections.emptyList();
        }
        List<TimeMasterForecastPointDto> sanitized = new ArrayList<>();
        for (TimeMasterForecastPointDto point : points) {
            if (point == null || point.date() == null || point.phaseId() == null || point.phaseId().isBlank()) {
                continue;
            }
            sanitized.add(new TimeMasterForecastPointDto(
                    point.date(),
                    Math.max(1, point.day()),
                    clamp(point.value(), 0, 100),
                    cleanText(point.phaseId(), "phase", 64)
            ));
        }
        return sanitized;
    }

    private List<String> cleanStringList(List<String> values, int maxItems, int maxLength) {
        if (values == null) {
            return Collections.emptyList();
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String item = cleanText(value, "", maxLength);
            if (!item.isBlank()) {
                cleaned.add(item);
            }
            if (cleaned.size() >= maxItems) {
                break;
            }
        }
        return cleaned;
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        return value != null && value.canConvertToInt() ? value.asInt() : fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String unwrapOpenAiResponse(String raw) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode content = root.at("/choices/0/message/content");
        if (content.isTextual()) {
            return extractJsonObject(content.asText());
        }
        return extractJsonObject(raw);
    }

    private String extractJsonObject(String value) {
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return "{}";
    }

    private List<ScheduleBlockDto> readBlocks(String json) {
        try {
            return objectMapper.readerForListOf(ScheduleBlockDto.class).readValue(json);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "计划草稿内容无法读取");
        }
    }

    private List<Long> readAppliedScheduleItemIds(AgentPlanDraftEntity draft) {
        String json = draft.getAppliedScheduleItemIdsJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(Long.class).readValue(json);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<ScheduleBlockDto> readBlocksNode(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<ScheduleBlockDto> blocks = new ArrayList<>();
        for (JsonNode item : node) {
            try {
                ScheduleBlockDto block = objectMapper.treeToValue(item, ScheduleBlockDto.class);
                if (block.endAt().isAfter(block.startAt())) {
                    blocks.add(block);
                }
            } catch (Exception ignored) {
                // Skip malformed blocks while preserving the rest of the plan.
            }
        }
        return blocks;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private List<String> readLimitedStringList(JsonNode node) {
        List<String> values = readStringList(node);
        List<String> limited = new ArrayList<>();
        for (String value : values) {
            String cleaned = cleanText(value, "", 160);
            if (!cleaned.isBlank()) {
                limited.add(cleaned);
            }
            if (limited.size() >= 8) {
                break;
            }
        }
        return limited;
    }

    private String cleanText(String value, String fallback, int maxLength) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()) {
            cleaned = fallback == null ? "" : fallback;
        }
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    private List<String> mergeWarnings(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>();
        merged.addAll(first == null ? Collections.emptyList() : first);
        merged.addAll(second == null ? Collections.emptyList() : second);
        return merged;
    }

    private List<Integer> allIndexes(List<ScheduleBlockDto> blocks) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            indexes.add(i);
        }
        return indexes;
    }

    private void rejectInternalBlockConflicts(List<ScheduleBlockDto> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            ScheduleBlockDto current = blocks.get(i);
            for (int j = i + 1; j < blocks.size(); j++) {
                ScheduleBlockDto next = blocks.get(j);
                if (current.startAt().isBefore(next.endAt()) && next.startAt().isBefore(current.endAt())) {
                    throw new ApiException(HttpStatus.CONFLICT, "计划草稿内部存在时间冲突：「" + current.title() + "」与「" + next.title() + "」。");
                }
            }
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "null";
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record ParsedAgentResponse(
            String title,
            String advice,
            String reasoningSummary,
            List<ScheduleBlockDto> blocks,
            List<String> warnings
    ) {
    }

    private record RefinedAgentRequest(
            String intent,
            String polishedQuestion,
            String timeRange,
            List<String> preferences,
            List<String> constraints,
            List<String> warnings
    ) {
    }
}
