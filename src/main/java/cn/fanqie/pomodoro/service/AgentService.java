package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.agent.LlmClient;
import cn.fanqie.pomodoro.domain.AgentKind;
import cn.fanqie.pomodoro.domain.PlanDraftStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentAdviceRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentAdviceResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.ApplyPlanRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleBlockDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
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
    public List<ScheduleItemDto> applyPlan(Long draftId, ApplyPlanRequest request) {
        AgentPlanDraftEntity draft = planDrafts.findById(draftId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "计划草稿不存在"));
        if (draft.getStatus() != PlanDraftStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "计划草稿已经处理过");
        }
        List<ScheduleBlockDto> blocks = readBlocks(draft.getScheduleBlocksJson());
        List<Integer> indexes = request == null || request.blockIndexes() == null || request.blockIndexes().isEmpty()
                ? allIndexes(blocks)
                : request.blockIndexes();
        List<ScheduleItemDto> created = new ArrayList<>();
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= blocks.size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "计划块索引无效");
            }
            created.add(scheduleService.createFromBlock(blocks.get(index)));
        }
        draft.setStatus(PlanDraftStatus.APPLIED);
        draft.setAppliedAt(now());
        return created;
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

    private AgentConversationEntity saveConversation(AgentKind kind, String userMessage, String summary, String raw) {
        AgentConversationEntity entity = new AgentConversationEntity();
        entity.setKind(kind);
        entity.setUserMessage(userMessage);
        entity.setResponseSummary(summary);
        entity.setRawResponse(raw);
        entity.setCreatedAt(now());
        return conversations.save(entity);
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
            cleaned = fallback;
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
