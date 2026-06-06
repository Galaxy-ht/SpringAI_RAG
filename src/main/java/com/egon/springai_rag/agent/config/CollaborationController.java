package com.egon.springai_rag.agent.config;

import com.egon.springai_rag.agent.AdvisorAgent;
import com.egon.springai_rag.agent.chain.ChainAgent;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多 Agent 协作 REST 控制器。
 * <p>
 * 提供 Router、Chain、Orchestrator 三种多 Agent 协作模式的 API 端点。
 *
 * <h3>端点说明</h3>
 * <ul>
 *   <li>POST /api/agent/router — Router Agent：意图分类 + 动态路由</li>
 *   <li>POST /api/agent/chain — Chain Agent：顺序管道执行</li>
 *   <li>POST /api/agent/orchestrate — Orchestrator Agent：任务分解 + 并行执行 + 合成</li>
 *   <li>GET  /api/agent/collaboration/info — 多 Agent 协作信息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/agent")
public class CollaborationController {

    private final Map<String, AdvisorAgent> agents;

    public CollaborationController(Map<String, AdvisorAgent> agents) {
        this.agents = agents;
    }

    /**
     * Router Agent — 意图分类 + 动态路由。
     */
    @PostMapping("/router")
    public Map<String, Object> routerExecute(@RequestBody String task) {
        AdvisorAgent router = agents.get("routerAgent");
        if (router == null) {
            throw new IllegalStateException("Router Agent 未注册");
        }
        String result = router.execute(task);
        return Map.of(
                "agent", router.getName(),
                "task", task,
                "result", result
        );
    }

    /**
     * Chain Agent — 顺序管道执行。
     * <p>
     * 请求体可选包含 chain 字段指定 Agent 链，默认使用配置的 default-chain。
     * 示例：{"task": "...", "chain": ["reactAgent", "reflectionAgent"]}
     */
    @PostMapping("/chain")
    public Map<String, Object> chainExecute(@RequestBody ChainRequest request) {
        AdvisorAgent chainAgent = agents.get("chainAgent");
        if (chainAgent == null) {
            throw new IllegalStateException("Chain Agent 未注册");
        }

        String result;
        if (request.chain != null && !request.chain.isEmpty()) {
            result = ((ChainAgent) chainAgent).execute(request.task, request.chain);
        } else {
            result = chainAgent.execute(request.task);
        }

        return Map.of(
                "agent", chainAgent.getName(),
                "task", request.task,
                "chain", request.chain != null ? request.chain : "default",
                "result", result
        );
    }

    /**
     * Orchestrator Agent — 任务分解 + 并行执行 + 结果合成。
     */
    @PostMapping("/orchestrate")
    public Map<String, Object> orchestrateExecute(@RequestBody String task) {
        AdvisorAgent orchestrator = agents.get("orchestratorAgent");
        if (orchestrator == null) {
            throw new IllegalStateException("Orchestrator Agent 未注册");
        }
        String result = orchestrator.execute(task);
        return Map.of(
                "agent", orchestrator.getName(),
                "task", task,
                "result", result
        );
    }

    /**
     * 多 Agent 协作信息。
     */
    @GetMapping("/collaboration/info")
    public Map<String, Object> collaborationInfo() {
        List<String> availableWorkers = agents.keySet().stream()
                .filter(k -> !k.contains("Agent") || k.equals("reactAgent")
                        || k.equals("reflectionAgent") || k.equals("planAndExecuteAgent"))
                .sorted()
                .toList();

        return Map.of(
                "patterns", List.of("router", "chain", "orchestrate"),
                "availableWorkers", availableWorkers,
                "endpoints", List.of(
                        "/api/agent/router",
                        "/api/agent/chain",
                        "/api/agent/orchestrate"
                )
        );
    }

    /**
     * Chain 请求 DTO。
     */
    public record ChainRequest(String task, List<String> chain) {}
}