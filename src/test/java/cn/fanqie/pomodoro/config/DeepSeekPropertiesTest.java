package cn.fanqie.pomodoro.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertiesConfig.class));

    @Test
    void defaultsToOfficialDeepSeekOpenAiCompatibleUrl() {
        contextRunner
                .withPropertyValues(
                        "fanqie.agent.api-key=test-key",
                        "fanqie.agent.model=deepseek-chat",
                        "fanqie.agent.timeout-seconds=45",
                        "fanqie.agent.mock-response="
                )
                .run(context -> {
                    DeepSeekProperties properties = context.getBean(DeepSeekProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("https://api.deepseek.com");
                });
    }

    @Test
    void ignoresOpenAiGatewayFallbackForDeepSeekConfig() {
        contextRunner
                .withSystemProperties("OPENAI_BASE_URL=https://example-gateway.local")
                .withPropertyValues(
                        "fanqie.agent.api-key=test-key",
                        "fanqie.agent.model=deepseek-chat",
                        "fanqie.agent.timeout-seconds=45",
                        "fanqie.agent.mock-response="
                )
                .run(context -> {
                    DeepSeekProperties properties = context.getBean(DeepSeekProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("https://api.deepseek.com");
                });
    }

    @Configuration
    @EnableConfigurationProperties(DeepSeekProperties.class)
    static class PropertiesConfig {
    }
}
