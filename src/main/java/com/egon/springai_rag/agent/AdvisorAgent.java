package com.egon.springai_rag.agent;

import java.util.List;

/**
 * Agent 接口 — 定义 Agent 的基本行为契约。
 * <p>
 * 所有 Agent（ReAct、Reflection、PlanAndExecute）都必须实现此接口，
 * 通过 {@code Map<String, AdvisorAgent>} 注入到 Controller 中。
 */
public interface AdvisorAgent {

    /**
     * @param task 任务描述（自然语言）
     * @return Agent 执行结果
     */
    String execute(String task);

    /**
     * @return Agent 名称，用于 API 选择和日志标识
     */
    String getName();

    /**
     * @return 该 Agent 可用的工具名称列表
     */
    List<String> getAvailableTools();
}