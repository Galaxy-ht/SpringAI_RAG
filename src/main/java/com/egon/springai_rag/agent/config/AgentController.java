package com.egon.springai_rag.agent.config;

import com.egon.springai_rag.agent.AdvisorAgent;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent REST 控制器。
 * <p>
 * 通过 {@code Map<String, AdvisorAgent>} 注入所有 Agent 实现，
 * 支持按名称选择 Agent 执行任务。
 *
 * <h3>端点说明</h3>
 * <ul>
 *   <li>GET  /api/agent/list — 列出所有可用 Agent 及其工具</li>
 *   <li>POST /api/agent/execute?agent=xxx — 通用执行端点</li>
 *   <li>POST /api/agent/react — 快捷端点：ReAct Agent</li>
 *   <li>POST /api/agent/reflect — 快捷端点：Reflection Agent</li>
 *   <li>POST /api/agent/plan-execute — 快捷端点：Plan-and-Execute Agent</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final Map<String, AdvisorAgent> agents;

    public AgentController(Map<String, AdvisorAgent> agents) {
        this.agents = agents;
    }

    /**
     * 列出所有可用 Agent 及其工具。
     */
    @GetMapping("/list")
    public Map<String, Object> listAgents() {
        return Map.of("agents", agents.entrySet().stream()
                .map(e -> Map.of(
                        "bean", e.getKey(),
                        "name", e.getValue().getName(),
                        "tools", e.getValue().getAvailableTools()
                ))
                .collect(Collectors.toList()));
    }

    /**
     * 通用执行端点 — 按名称选择 Agent。
     */
    @PostMapping("/execute")
    public Map<String, Object> execute(
            @RequestParam String agent,
            @RequestBody String task) {

        AdvisorAgent selectedAgent = agents.get(agent);
        if (selectedAgent == null) {
            throw new IllegalArgumentException(
                    "未知 Agent: " + agent + "，可用: " + agents.keySet());
        }

        String result = selectedAgent.execute(task);
        return Map.of(
                "agent", selectedAgent.getName(),
                "task", task,
                "result", result
        );
    }

    /**
     * 快捷端点：ReAct Agent。
     */
    @PostMapping("/react")
    public Map<String, Object> reactExecute(@RequestBody String task) {
        AdvisorAgent agent = agents.get("reactAgent");
        if (agent == null) {
            throw new IllegalStateException("ReAct Agent 未注册，请确保实现了 ReActAgent 并标记为 @Component(\"reactAgent\")");
        }
        return Map.of("agent", agent.getName(), "task", task, "result", agent.execute(task));
    }

    /**
     * 快捷端点：Reflection Agent。
     */
    @PostMapping("/reflect")
    public Map<String, Object> reflectExecute(@RequestBody String task) {
        AdvisorAgent agent = agents.get("reflectionAgent");
        if (agent == null) {
            throw new IllegalStateException("Reflection Agent 未注册，请确保实现了 ReflectionAgent 并标记为 @Component(\"reflectionAgent\")");
        }
        return Map.of("agent", agent.getName(), "task", task, "result", agent.execute(task));
    }

    /**
     * 快捷端点：Plan-and-Execute Agent。
     */
    @PostMapping("/plan-execute")
    public Map<String, Object> planExecute(@RequestBody String task) {
        AdvisorAgent agent = agents.get("planAndExecuteAgent");
        if (agent == null) {
            throw new IllegalStateException("Plan-and-Execute Agent 未注册，请确保实现了 PlanAndExecuteAgent 并标记为 @Component(\"planAndExecuteAgent\")");
        }
        return Map.of("agent", agent.getName(), "task", task, "result", agent.execute(task));
    }
}