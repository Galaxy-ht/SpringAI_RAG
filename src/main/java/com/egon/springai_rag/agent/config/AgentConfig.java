package com.egon.springai_rag.agent.config;

import com.egon.springai_rag.agent.text2sql.Text2SqlTool;
import com.egon.springai_rag.agent.tools.ToolDefinitions;
import com.egon.springai_rag.retrieval.HybridRetrievalService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Agent 配置 — 注册 ToolCallback Bean 供所有 Agent 注入。
 * <p>
 * 所有工具以 {@code List<ToolCallback>} 的形式注入到 Agent 中，
 * Spring AI 会自动发现 Bean 类型为 {@link ToolCallback} 的所有实例。
 */
@Configuration
public class AgentConfig {

    /**
     * 注册所有 Agent 可用的工具。
     * <p>
     * 新增工具时只需在此方法中添加即可，所有 Agent 自动获得新工具。
     */
    @Bean
    public List<ToolCallback> agentTools(HybridRetrievalService hybridRetrievalService,
                                          Text2SqlTool text2SqlTool) {
        return List.of(
                // P1-P4: RAG 检索管道
                ToolDefinitions.ragSearchTool(hybridRetrievalService),
                // P5: 基础工具
                ToolDefinitions.calculatorTool(),
                ToolDefinitions.dateTimeTool(),
                // P8: text2SQL — 自然语言查询数据库
                text2SqlTool.createToolCallback()
        );
    }
}