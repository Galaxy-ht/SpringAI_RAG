package com.egon.springai_rag.agent.router;

import com.egon.springai_rag.agent.AbstractAgent;
import com.egon.springai_rag.agent.AdvisorAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Router Agent — 意图分类 + 动态路由到最合适的 Worker Agent。
 * <p>
 * 核心流程：
 * <pre>
 *   用户提问 → 意图分类(analyze) → 选择 Worker Agent → 委托执行 → 返回结果
 * </pre>
 * <p>
 * 与 LangGraph 的 Conditional Edges 模式对应：根据任务特征动态选择执行路径。
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>构建意图分类 prompt：让 LLM 判断任务最适合哪个 Agent</li>
 *   <li>解析分类结果：将 LLM 输出映射到 Agent bean 名称</li>
 *   <li>兜底策略：分类失败时使用默认 Agent</li>
 * </ol>
 * <p>
 * <b>分类 Prompt 参考：</b>
 * <pre>
 * 你是一个任务分类专家。请分析以下任务，判断它最适合由哪个 Agent 处理。
 *
 * 可用 Agent 及其专长：
 * - reactAgent: 适合需要调用工具、搜索知识库、获取实时信息的任务
 * - reflectionAgent: 适合需要深度推理、自我纠错、高质量输出的任务
 * - planAndExecuteAgent: 适合需要多步骤规划、有明确依赖关系的复杂任务
 *
 * 任务：{task}
 *
 * 请只输出一个 Agent 名称（reactAgent / reflectionAgent / planAndExecuteAgent），不要输出其他内容。
 * </pre>
 */
@Slf4j
@Component("routerAgent")
public class RouterAgent extends AbstractAgent {

    private final Map<String, AdvisorAgent> agents;

    @Value("${app.agent.multi.router.fallback-agent:reactAgent}")
    private String fallbackAgent;

    public RouterAgent(ChatClient.Builder chatClientBuilder,
                       List<ToolCallback> tools,
                       Map<String, AdvisorAgent> agents) {
        super(chatClientBuilder, tools, "Router-Agent");
        this.agents = agents;
    }

    @Override
    public String execute(String task) {
        // 1. 意图分类
        String targetAgent = analyze(task);
        log.info("Router 将任务路由到: {}", targetAgent);

        // 2. 获取目标 Agent，不可用时使用兜底
        AdvisorAgent agent = agents.get(targetAgent);
        if (agent == null) {
            log.warn("目标 Agent {} 不存在，使用兜底 Agent: {}", targetAgent, fallbackAgent);
            agent = agents.get(fallbackAgent);
        }
        if (agent == null) {
            return "路由失败：没有可用的 Agent";
        }

        // 3. 委托执行
        try {
            return agent.execute(task);
        } catch (Exception e) {
            log.error("Router 委托执行失败, targetAgent={}", targetAgent, e);
            return "路由执行失败：" + e.getMessage();
        }
    }

    /**
     * 意图分类 — 分析任务并返回最合适的 Agent bean 名称。
     *
     * @param task 用户任务
     * @return Agent bean 名称（reactAgent / reflectionAgent / planAndExecuteAgent）
     */
    public String analyze(String task) {
        // 实现意图分类逻辑
        // Step 1: 构建分类 prompt（参考类注释中的模板，或自己设计更好的）
        //   String prompt = """
        //           你是一个任务分类专家...
        //           任务：{task}
        //           """;
        //   String fullPrompt = prompt.replace("{task}", task);
        //
        // Step 2: 调用 chatClient 获取分类结果
        //   String result = chatClient.prompt().user(fullPrompt).call().content();
        //
        // Step 3: 清理 LLM 输出（去除空白、换行、标点等）
        //   if (result != null) { result = result.trim(); }
        //
        // Step 4: 验证分类结果是否在可用 Agent 列表中
        //   if (agents.containsKey(result)) { return result; }
        //
        // Step 5: 分类无效时返回 fallbackAgent
        //   log.warn("意图分类失败，使用兜底 Agent: {}", fallbackAgent);
        //   return fallbackAgent;

        String prompt = """
                 你是一个任务分类专家。请分析以下任务，判断它最适合由哪个 Agent 处理。
                
                 可用 Agent 及其专长：
                 - reactAgent: 适合需要调用工具、搜索知识库、获取实时信息的任务
                 - reflectionAgent: 适合需要深度推理、自我纠错、高质量输出的任务
                 - planAndExecuteAgent: 适合需要多步骤规划、有明确依赖关系的复杂任务
                
                 任务：{task}
                
                 请只输出一个 Agent 名称（reactAgent / reflectionAgent / planAndExecuteAgent），不要输出其他内容。
                """.replace("{task}", task);

        String result = chatClient.prompt().user(prompt).call().content();
        if (result == null) {
            log.warn("LLM无响应");
            return fallbackAgent;
        }

        result = result.trim();
        if (agents.containsKey(result)) {
            return result;
        }

        log.warn("意图分类失败，使用兜底 Agent: {}", fallbackAgent);
        return fallbackAgent;
    }
}