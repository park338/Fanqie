package cn.fanqie.pomodoro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "fanqie.agent")
public record DeepSeekProperties(
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        String mockResponse
) {
    public DeepSeekProperties {
        baseUrl = StringUtils.hasText(baseUrl) ? baseUrl : "https://api.deepseek.com";
        apiKey = apiKey == null ? "" : apiKey;
        model = StringUtils.hasText(model) ? model : "deepseek-chat";
        timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 45;
        mockResponse = mockResponse == null ? "" : mockResponse;
    }
}
