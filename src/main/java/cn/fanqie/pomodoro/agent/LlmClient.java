package cn.fanqie.pomodoro.agent;

public interface LlmClient {
    String chat(String systemPrompt, String userPrompt);
}
