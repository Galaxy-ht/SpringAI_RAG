package com.egon.springai_rag.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Agent 抽象基类 — 提供 ChatClient 初始化、工具列表、名称管理等公共逻辑。
 * <p>
 * 子类只需实现 {@link #execute(String)} 方法。
 */
public abstract class AbstractAgent implements AdvisorAgent {

    protected final ChatClient chatClient;
    protected final List<ToolCallback> tools;
    protected final String name;

    protected AbstractAgent(ChatClient.Builder chatClientBuilder,
                            List<ToolCallback> tools,
                            String name) {
        this.chatClient = chatClientBuilder.build();
        this.tools = tools;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getAvailableTools() {
        return tools.stream()
                .map(t -> t.getToolDefinition().name())
                .toList();
    }
}