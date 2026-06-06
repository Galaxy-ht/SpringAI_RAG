package com.egon.springai_rag.agent.react;

import com.egon.springai_rag.agent.AbstractAgent;
import com.egon.springai_rag.agent.WorkerAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ReAct Agent — 基于 Reasoning + Acting 循环的智能体。
 * <p>
 * 核心流程：
 * <pre>
 *   用户提问 → Thought(思考) → Action(选择工具) → Observation(观察结果)
 *   → Thought(继续思考) → Action(finish) → Final Answer(最终答案)
 * </pre>
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>调用 {@link ReActLoop#run(String, int)} 执行 ReAct 循环</li>
 *   <li>处理异常情况（如 LLM 返回无法解析的输出）</li>
 *   <li>超时处理和优雅降级</li>
 * </ol>
 * <p>
 * 实现提示：大部分核心逻辑已在 ReActLoop 中，ReActAgent 主要负责：
 * <ul>
 *   <li>从配置文件读取 maxIterations</li>
 *   <li>调用 reActLoop.run(task, maxIterations)</li>
 *   <li>包装异常为友好的错误消息</li>
 * </ul>
 */
@Slf4j
@Component("reactAgent")
@WorkerAgent
public class ReActAgent extends AbstractAgent {

    private final ReActLoop reActLoop;

    @Value("${app.agent.react.max-iterations:2}")
    private int maxIterations;

    public ReActAgent(ChatClient.Builder chatClientBuilder,
                      List<ToolCallback> tools,
                      ReActLoop reActLoop) {
        super(chatClientBuilder, tools, "ReAct-Agent");
        this.reActLoop = reActLoop;
    }

    @Override
    public String execute(String task) {
        // 实现 ReAct Agent 执行逻辑
        // 1. 调用 reActLoop.run(task, maxIterations)
        // 2. 捕获异常并返回友好的错误消息
        // 3. 如果返回 null 或空字符串，返回兜底消息

        // 【优化】统一初始化和兜底逻辑，避免空字符串中间态
        String answer;
        try {
            answer = reActLoop.run(task, maxIterations);
        } catch (Exception e) {
            log.error("ReAct Agent 执行异常", e);
            return "遇到未知错误，请联系管理员或稍后再试";
        }
        return StringUtils.isBlank(answer) ? "遇到未知错误，请联系管理员或稍后再试" : answer;
    }
}