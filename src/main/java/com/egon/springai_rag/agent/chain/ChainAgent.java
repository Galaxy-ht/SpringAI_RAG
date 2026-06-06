package com.egon.springai_rag.agent.chain;

import com.egon.springai_rag.agent.AbstractAgent;
import com.egon.springai_rag.agent.AdvisorAgent;
import com.egon.springai_rag.agent.WorkerAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chain Agent — 顺序管道执行多个 Agent。
 * <p>
 * 核心流程：
 * <pre>
 *   用户提问 → Agent 1 → Agent 2 → ... → Agent N → 最终答案
 * </pre>
 * <p>
 * 每个 Agent 的输出作为下一个 Agent 的输入，形成处理管道。
 * 典型场景：ReAct 搜集信息 → Reflection 优化质量 → 返回高质量答案。
 * <p>
 * 与 LangGraph 的 Sequential Nodes 模式对应：固定顺序的节点链。
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>顺序执行循环：按 chain 列表顺序逐个调用 Agent</li>
 *   <li>上下文传递：将上一阶段的输出作为下一阶段的输入</li>
 *   <li>阶段记录：记录每个阶段的 Agent 名称、输入、输出、耗时</li>
 *   <li>失败处理：某个阶段失败时，决定是否继续或终止</li>
 * </ol>
 */
@Slf4j
@Component("chainAgent")
public class ChainAgent extends AbstractAgent {

    private final Map<String, AdvisorAgent> agents;

    @Value("${app.agent.multi.chain.default-chain:reactAgent,reflectionAgent}")
    private List<String> defaultChain;

    @Value("${app.agent.multi.chain.max-chain-length:5}")
    private int maxChainLength;

    public ChainAgent(ChatClient.Builder chatClientBuilder,
                      List<ToolCallback> tools,
                      @WorkerAgent Map<String, AdvisorAgent> agents) {
        super(chatClientBuilder, tools, "Chain-Agent");
        this.agents = agents;
    }

    @Override
    public String execute(String task) {
        return execute(task, defaultChain);
    }

    /**
     * 按指定链顺序执行 Agent。
     *
     * @param task       用户任务
     * @param agentChain Agent bean 名称列表
     * @return 最终答案
     */
    public String execute(String task, List<String> agentChain) {
        // 实现 Chain 执行逻辑
        // Step 1: 验证链 — 过滤掉不存在的 Agent、限制链长度
        //   List<String> validChain = new ArrayList<>();
        //   for (String name : agentChain) {
        //       if (agents.containsKey(name)) { validChain.add(name); }
        //       if (validChain.size() >= maxChainLength) break;
        //   }
        //   if (validChain.isEmpty()) { return "链执行失败：没有可用的 Agent"; }
        //
        // Step 2: 初始化当前输入为原始 task
        //   String currentInput = task;
        //
        // Step 3: 循环执行每个 Agent
        //   for (String agentName : validChain) {
        //       AdvisorAgent agent = agents.get(agentName);
        //       long start = System.currentTimeMillis();
        //       try {
        //           String output = agent.execute(currentInput);
        //           long duration = System.currentTimeMillis() - start;
        //           log.info("阶段 {} 完成, 耗时 {}ms", agentName, duration);
        //           // 【关键】当前输出作为下一阶段的输入
        //           currentInput = output;
        //       } catch (Exception e) {
        //           // 【决策点】失败时应该终止链还是跳过继续？
        //           log.error("阶段 {} 失败", agentName, e);
        //           return "链执行在阶段 " + agentName + " 失败：" + e.getMessage();
        //       }
        //   }
        //
        // Step 4: 返回最后阶段的输出
        //   return currentInput;

        List<String> validChain = new ArrayList<>();
        for (String name : agentChain) {
            if (agents.containsKey(name) && !validChain.contains(name)) {
                validChain.add(name);
            }
            if (validChain.size() >= maxChainLength) break;
        }
        if (validChain.isEmpty()) { return "链执行失败：没有可用的 Agent"; }

        String currentInput = task;

        for (String agentName : validChain) {
            AdvisorAgent agent = agents.get(agentName);
            long start = System.currentTimeMillis();
            try {
                String output = agent.execute(currentInput);
                long duration = System.currentTimeMillis() - start;
                log.info("阶段 {} 完成, 耗时 {}ms", agentName, duration);
                // 【关键】当前输出作为下一阶段的输入
                currentInput = output;
            } catch (Exception e) {
                // 【决策点】失败时应该终止链还是跳过继续？
                log.error("阶段 {} 失败", agentName, e);
                return "链执行在阶段 " + agentName + " 失败：" + e.getMessage();
            }
        }

        return currentInput;
    }
}