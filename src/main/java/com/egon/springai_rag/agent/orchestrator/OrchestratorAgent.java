package com.egon.springai_rag.agent.orchestrator;

import com.egon.springai_rag.agent.AbstractAgent;
import com.egon.springai_rag.agent.AdvisorAgent;
import com.egon.springai_rag.agent.WorkerAgent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Orchestrator Agent — 任务分解 + Worker 分配 + 并行执行 + 结果合成。
 * <p>
 * 核心流程：
 * <pre>
 *   用户提问
 *     → decompose(任务分解)
 *     → assignWorkers(Worker 分配)
 *     → executeSubtasks(并行/顺序执行)
 *     → synthesize(结果合成)
 *     → 最终答案
 * </pre>
 * <p>
 * 这是三种多 Agent 模式中最复杂的，对应 LangGraph 的 Supervisor 模式和
 * CrewAI 的 Hierarchical Process。
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>任务分解(decompose)：让 LLM 将复杂任务拆分为子任务，解析 JSON 输出</li>
 *   <li>Worker 分配(assignWorkers)：根据 capability 为每个子任务匹配最合适的 Agent</li>
 *   <li>并行执行(executeSubtasks)：独立子任务并行执行，依赖子任务顺序执行</li>
 *   <li>结果合成(synthesize)：汇总所有子任务结果，生成最终答案</li>
 * </ol>
 * <p>
 * <b>Decompose Prompt 参考：</b>
 * <pre>
 * 你是一个任务分解专家。请将以下任务分解为独立的子任务。
 *
 * 原始任务：{task}
 *
 * 可用 Worker Agent 及其能力：
 * - reactAgent: 搜索知识库、调用工具、获取实时信息
 * - reflectionAgent: 深度推理、质量评估、自我纠错
 * - planAndExecuteAgent: 多步骤规划、结构化执行
 *
 * 输出格式（JSON 数组）：
 * [
 *   {"id": 1, "description": "子任务描述", "dependencies": [], "capability": "search"},
 *   {"id": 2, "description": "子任务描述", "dependencies": [1], "capability": "reason"}
 * ]
 *
 * 要求：
 * - 每个子任务应清晰、可独立执行
 * - dependencies 列出必须在此子任务之前完成的子任务 ID
 * - capability 标注所需能力：search / reason / plan / verify
 * </pre>
 */
@Slf4j
@Component("orchestratorAgent")
public class OrchestratorAgent extends AbstractAgent {

    private final Map<String, AdvisorAgent> agents;

    @Value("${app.agent.multi.orchestrator.max-subtasks:10}")
    private int maxSubtasks;

    @Value("${app.agent.multi.orchestrator.max-concurrent-workers:3}")
    private int maxConcurrentWorkers;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public OrchestratorAgent(ChatClient.Builder chatClientBuilder,
                             List<ToolCallback> tools,
                             @WorkerAgent Map<String, AdvisorAgent> agents) {
        super(chatClientBuilder, tools, "Orchestrator-Agent");
        this.agents = agents;
    }

    @Override
    public String execute(String task) {
        // 1. 任务分解
        List<Subtask> subtasks = decompose(task);
        log.info("Orchestrator 分解出 {} 个子任务", subtasks.size());
        log.info("{}", subtasks);

        // 2. Worker 分配
        assignWorkers(subtasks);
        log.info("Worker 分配完成: {}", subtasks.stream()
                .map(s -> "[" + s.id + "→" + s.assignedWorker + "]")
                .collect(Collectors.joining(", ")));

        // 3. 执行子任务
        executeSubtasks(subtasks);
        log.info("子任务执行完成");
        log.info("{}", subtasks);

        // 4. 结果合成
        return synthesize(task, subtasks);
    }

    /**
     * 任务分解 — 将复杂任务拆分为子任务列表。
     */
    public List<Subtask> decompose(String task) {
        // 实现任务分解逻辑
        // Step 1: 构建 decompose prompt（参考类注释中的模板）
        //   String prompt = """
        //           你是一个任务分解专家...
        //           原始任务：{task}
        //           """;
        //   String fullPrompt = prompt.replace("{task}", task);
        //
        // Step 2: 调用 chatClient 获取子任务列表
        //   String result = chatClient.prompt().user(fullPrompt).call().content();
        //
        // Step 3: 解析 LLM 输出的 JSON 数组
        //   - 提取 [...] 部分
        //   - 逐个解析每个子任务对象：id, description, dependencies, capability
        //   - 提示：可以用正则 \\{.*?\\} 匹配每个 JSON 对象，再提取字段
        //   - 限制数量不超过 maxSubtasks
        //
        // Step 4: 如果解析失败或结果为空，返回单个子任务（原始任务本身）
        //   return List.of(new Subtask(1, task, List.of(), "search"));

        String prompt = """
                你是一个任务分解专家。请将以下任务分解为独立的子任务。
                
                 原始任务：{task}
                
                 可用 Worker Agent 及其能力：
                 - reactAgent: 搜索知识库、调用工具、获取实时信息
                 - reflectionAgent: 深度推理、质量评估、自我纠错
                 - planAndExecuteAgent: 多步骤规划、结构化执行
                
                 输出格式（JSON 数组）：
                 [
                   {"id": 1, "description": "子任务描述", "dependencies": [], "capability": "search"},
                   {"id": 2, "description": "子任务描述", "dependencies": [1], "capability": "reason"},
                   {"id": 3, "description": "子任务描述", "dependencies": [2], "capability": "plan"}
                   {"id": 4, "description": "子任务描述", "dependencies": [1, 2], "capability": "verify"}
                 ]
                
                 要求：
                 - 每个子任务应清晰、可独立执行
                 - id为默认自增的整数
                 - description为该任务需要执行的prompt
                 - capability为枚举值{search, reason, plan, verify}，对应可用 Worker Agent
                 - 输出标准格式的纯JSON，不需要任何其他描述
                 - 子任务数最多不能超过{maxSubtasks}个
                """.replace("{task}", task).replace("{maxSubtasks}", String.valueOf(maxSubtasks));

        String result = chatClient.prompt().user(prompt).call().content();
        if (StringUtils.isBlank(result)) {
            throw new IllegalStateException("LLM响应失败");
        }

        // 清理 LLM 输出噪声：去 markdown 代码块、去前后非 JSON 文本
        String cleaned = result
                .replaceAll("```(?:json)?\\s*\\n?", "")
                .replaceAll("```\\s*", "")
                .trim();

        try {
            List<Subtask> parsed = objectMapper.readValue(cleaned, new TypeReference<List<Subtask>>() {});
            // 限制子任务数量
            return parsed.size() > maxSubtasks ? parsed.subList(0, maxSubtasks) : parsed;
        } catch (Exception e) {
            log.error("解析LLM输出失败\nllmOutput:\n{}\n", result, e);
            throw new IllegalStateException(e);
        }

    }

    /**
     * Worker 分配 — 为每个子任务匹配最合适的 Agent。
     */
    public void assignWorkers(List<Subtask> subtasks) {
        // 实现 Worker 分配逻辑
        // 根据子任务的 capability 匹配最合适的 Agent：
        //   "search" → reactAgent
        //   "reason" / "verify" → reflectionAgent
        //   "plan" → planAndExecuteAgent
        //
        // 提示：可以使用 switch 表达式或 Map 映射
        //   String worker = switch (subtask.capability) {
        //       case "reason", "verify" -> "reflectionAgent";
        //       case "plan" -> "planAndExecuteAgent";
        //       default -> "reactAgent";
        //   };
        //
        // 如果首选 Worker 不可用，使用 reactAgent 作为兜底
        //   if (!agents.containsKey(worker)) { worker = "reactAgent"; }
        //
        // 设置 subtask.assignedWorker = worker;

        subtasks.forEach(subtask -> {
            String worker = switch (subtask.capability) {
                case "reason", "verify" -> "reflectionAgent";
                case "plan" -> "planAndExecuteAgent";
                default -> "reactAgent";
            };
            // 如果首选 Worker 不可用，使用 reactAgent 兜底
            if (!agents.containsKey(worker)) {
                log.warn("Worker {} 不可用，使用 reactAgent 兜底", worker);
                worker = "reactAgent";
            }
            subtask.setAssignedWorker(worker);
        });
    }

    /**
     * 执行子任务 — 独立子任务并行，依赖子任务顺序执行。
     */
    public void executeSubtasks(List<Subtask> subtasks) {
        // 实现子任务执行逻辑
        // Step 1: 分离独立子任务（dependencies 为空）和依赖子任务
        //   List<Subtask> independent = subtasks.stream()
        //       .filter(s -> s.dependencies.isEmpty()).toList();
        //   List<Subtask> dependent = subtasks.stream()
        //       .filter(s -> !s.dependencies.isEmpty()).toList();
        //
        // Step 2: 并行执行独立子任务
        //   使用 ExecutorService（最多 maxConcurrentWorkers 个线程）：
        //   ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentWorkers);
        //   对每个独立子任务提交 executor.submit(() -> executeSingleSubtask(subtask))
        //   等待所有 Future 完成（设置超时，如 60 秒）
        //
        // Step 3: 顺序执行依赖子任务
        //   for (Subtask subtask : dependent) {
        //       executeSingleSubtask(subtask);
        //   }
        //
        // Step 4: executeSingleSubtask 的实现：
        //   - 从 agents Map 获取 worker
        //   - 构建输入（包含依赖子任务的结果）
        //   - 调用 worker.execute(input)
        //   - 记录结果和状态（COMPLETED / FAILED）

        List<Subtask> independent = subtasks.stream()
                .filter(s -> s.dependencies.isEmpty()).toList();
        List<Subtask> dependent = subtasks.stream()
                .filter(s -> !s.dependencies.isEmpty()).toList();

        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentWorkers);
        try {
            independent.forEach(s -> executor.submit(() -> executeSingleSubtask(s, subtasks)));
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                    log.warn("子任务执行超时，强制终止");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        dependent.forEach(s -> executeSingleSubtask(s, subtasks));
    }

    private void executeSingleSubtask(Subtask subtask, List<Subtask> subtasks) {
        String taskInput = subtask.description;
        if (!subtask.dependencies.isEmpty()) {
            String context = subtasks.stream()
                    .filter(f -> subtask.dependencies.contains(f.id))
                    .map(m -> m.result)
                    .collect(Collectors.joining("\n"));
            taskInput = subtask.description + "\n上下文:\n" + context;
        }

        AdvisorAgent advisorAgent = agents.get(subtask.assignedWorker);
        if (advisorAgent == null) {
            subtask.setStatus("FAILED");
            subtask.setResult("Worker " + subtask.assignedWorker + " 不可用");
            log.error("Worker {} 不可用，子任务 {} 执行失败", subtask.assignedWorker, subtask.id);
            return;
        }

        try {
            String execute = advisorAgent.execute(taskInput);
            subtask.setStatus("SUCCESS");
            subtask.setResult(execute);
        } catch (Exception e) {
            subtask.setStatus("FAILED");
            subtask.setResult(e.getMessage());
            log.error("调用subAgent失败:{}\n{}\n", subtask.assignedWorker, subtask.description, e);
        }
    }

    /**
     * 结果合成 — 汇总所有子任务结果，生成最终答案。
     */
    public String synthesize(String task, List<Subtask> subtasks) {
        // 实现结果合成逻辑
        // Step 1: 构建子任务结果汇总文本
        //   StringBuilder sb = new StringBuilder();
        //   for (Subtask s : subtasks) {
        //       sb.append("子任务 ").append(s.id)
        //         .append(" [").append(s.status).append("]: ")
        //         .append(s.result != null ? s.result : "无结果")
        //         .append("\n---\n");
        //   }
        //
        // Step 2: 构建 synthesize prompt
        //   String prompt = """
        //       根据以下子任务执行结果，回答原始问题。
        //       原始问题：{task}
        //       子任务结果：
        //       {results}
        //       请综合以上结果，给出完整、准确的最终答案。
        //       """;
        //
        // Step 3: 调用 chatClient 生成最终答案
        //   String fullPrompt = prompt.replace("{task}", task)
        //       .replace("{results}", sb.toString());
        //   return chatClient.prompt().user(fullPrompt).call().content();

        StringBuilder sb = new StringBuilder();
        for (Subtask s : subtasks) {
            sb.append("子任务 ").append(s.id)
                    .append(" [").append(s.status).append("]: ")
                    .append(s.result != null ? s.result : "无结果")
                    .append("\n---\n");
        }

        String prompt = """
           根据以下子任务执行结果，回答原始问题。
           原始问题：{task}
           子任务结果：
           {results}
           请综合以上结果，给出完整、准确的最终答案。
           """.replace("{task}", task).replace("{results}", sb.toString());

        log.info("最终汇总：\n{}\n", prompt);
        String content = chatClient.prompt().user(prompt).call().content();
        if (StringUtils.isBlank(content)) {
            log.error("LLM 合成响应为空");
            return "结果合成失败，请稍后重试";
        }
        log.info("最终答案\n{}\n", content);
        return content;
    }

    /**
     * 子任务定义 — 内部 DTO。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subtask {
        public int id;
        public String description;
        public List<Integer> dependencies;
        public String capability;
        public String assignedWorker;
        public String status;
        public String result;

        public Subtask(int id, String description, List<Integer> dependencies, String capability) {
            this.id = id;
            this.description = description;
            this.dependencies = dependencies;
            this.capability = capability;
            this.status = "PENDING";
        }

        @Override
        public String toString() {
            return "Subtask{id=" + id + ", description='" + description + "', status=" + status + "}";
        }
    }
}