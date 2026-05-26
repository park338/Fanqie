package cn.fanqie.pomodoro.agent;

import cn.fanqie.pomodoro.config.DeepSeekProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class DeepSeekLlmClient implements LlmClient {
    private final DeepSeekProperties properties;
    private final RestClient restClient;

    public DeepSeekLlmClient(DeepSeekProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (StringUtils.hasText(properties.mockResponse())) {
            return properties.mockResponse();
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            return """
                    {
                      "title": "需要配置 DeepSeek",
                      "advice": "小茄还没有拿到 DeepSeek API Key。请在本地环境变量中配置 DEEPSEEK_API_KEY 后再让我生成更贴合你的计划。",
                      "reasoningSummary": "当前缺少模型凭据，因此返回本地降级建议。",
                      "blocks": [],
                      "warnings": ["DEEPSEEK_API_KEY 未配置"]
                    }
                    """;
        }
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "temperature", 0.4,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        String response = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .body(body)
                .retrieve()
                .body(String.class);
        return response == null ? "{}" : response;
    }

    public Duration timeout() {
        return Duration.ofSeconds(Math.max(5, properties.timeoutSeconds()));
    }
}
