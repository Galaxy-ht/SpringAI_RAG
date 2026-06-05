package com.egon.springai_rag.agent.planner;

import com.egon.springai_rag.agent.AbstractAgent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan-and-Execute Agent — 先规划后执行的智能体。
 * <p>
 * 核心流程：
 * <pre>
 *   用户提问 → 分解任务(Plan) → 逐步执行(Execute) → 汇总结果(Synthesize) → 最终答案
 * </pre>
 * <p>
 * 与 ReAct 的区别：Plan-and-Execute 在开始执行前先生成完整计划，适合复杂、多步骤任务。
 * ReAct 是每一步都重新思考，适合不确定性强、需要灵活调整的任务。
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>任务分解：将复杂任务拆分为独立的、可顺序执行的步骤</li>
 *   <li>步骤执行：按顺序执行每个步骤，使用合适的工具</li>
 *   <li>结果汇总：将所有步骤的结果整合为最终答案</li>
 *   <li>错误处理：某个步骤失败时，决定是否跳过或重试</li>
 * </ol>
 * <p>
 * <b>Plan Prompt 参考：</b>
 * <pre>
 * 你是一个任务规划专家。请将以下任务分解为独立的、可顺序执行的步骤。
 *
 * 任务：{task}
 *
 * 可用工具：
 * - rag_search: 搜索知识库
 * - calculator: 执行数学运算
 * - datetime: 获取当前时间
 *
 * 输出格式（每行一个步骤）：
 * Step 1: [工具名] [步骤描述]
 * Step 2: [工具名] [步骤描述]
 * ...
 *
 * 要求：
 * - 每个步骤应使用一个工具
 * - 步骤之间应有逻辑依赖关系
 * - 最后一步应为"汇总所有结果，生成最终答案"
 * </pre>
 */
@Slf4j
@Component("planAndExecuteAgent")
public class PlanAndExecuteAgent extends AbstractAgent {

    @Value("${app.agent.planner.max-steps:5}")
    private int maxSteps;

    public PlanAndExecuteAgent(ChatClient.Builder chatClientBuilder,
                               List<ToolCallback> tools) {
        super(chatClientBuilder, tools, "Plan-and-Execute-Agent");
    }

    @Override
    public String execute(String task) {
        // 实现 Plan-and-Execute Agent 执行逻辑
        // 1. 生成计划：
        //    List<Step> plan = generatePlan(task);
        // 2. 执行每个步骤：
        //    for (Step step : plan) {
        //        String result = executeStep(step);
        //        step.setResult(result);
        //    }
        // 3. 汇总结果：
        //    String finalAnswer = synthesizeResults(task, plan);
        // 4. 返回最终答案

        List<Step> plan = generatePlan(task);
        log.info("plan:{}", plan);
        for (Step step : plan) {
            String result = executeStep(step);
            step.setResult(result);
        }
        log.info("completed: {}", plan);

        return synthesizeResults(task, plan);
    }

    /**
     * 将复杂任务分解为可执行的步骤列表。
     */
    public List<Step> generatePlan(String task) {
        // 实现任务分解
        // 1. 构建 plan prompt（参考类注释中的模板）
        // 2. 调用 chatClient 获取步骤列表
        // 3. 解析 LLM 输出，构建 List<Step>
        // 4. 限制步骤数不超过 maxSteps
        String prompt = """
                 你是一个任务规划专家。请将以下任务分解为独立的、可顺序执行的步骤。
                
                 任务：{task}
                
                 可用工具：
                 - rag_search: 搜索知识库
                 - calculator: 执行数学运算
                 - datetime: 获取当前时间
                
                 输出格式（每行一个步骤）：
                 Step 1: [工具名] [工具调用参数]
                 Step 2: [工具名] [工具调用参数]
                 ...
                 其中[工具调用参数]中的内容应该是按工具列表定义中的格式化工具入参
                 如果工具不需要入参，则置为 {}
                
                
                 要求：
                 - 每个步骤应使用一个工具
                 - 步骤之间应有逻辑依赖关系
                 - 最后一步应为"汇总所有结果，生成最终答案"
                """;

        String fullPrompt = prompt.replace("{task}", task).replace("{maxSteps}", String.valueOf(maxSteps));
        String plan = chatClient.prompt().user(fullPrompt).call().content();

        return parseSteps(plan);
    }

    private List<Step> parseSteps(String output) {
        if (output == null || output.isEmpty()) {
            return new ArrayList<>();
        }
        List<Step> steps = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        Pattern pattern = Pattern.compile("^Step\\s+(\\d+):\\s+(.+)$");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                int stepNumber = Integer.parseInt(m.group(1));
                String rest = m.group(2); // 如 "toolName description ..."
                // 第一个空格前是工具名，后面是描述
                int firstSpace = rest.indexOf(' ');
                if (firstSpace == -1) {
                    // 没有描述的情况
                    steps.add(new Step(stepNumber, rest, ""));
                } else {
                    String toolName = rest.substring(0, firstSpace);
                    String description = rest.substring(firstSpace + 1);
                    steps.add(new Step(stepNumber, toolName, description));
                }
            }
        }
        return steps;
    }
    /**
     * 执行单个步骤。
     */
    public String executeStep(Step step) {
        // 实现步骤执行
        // 1. 根据 step.toolName 从 tools 列表中找到对应工具
        // 2. 调用 tool.call(step.description)
        // 3. 返回执行结果
        ToolCallback tool = tools.stream().filter(t -> t.getToolDefinition().name().equals(step.toolName)).findFirst().orElse(null);
        if (tool == null) {
            log.error("调用工具失败,step={}", step);
            step.setStatus("FAILED");
            return "工具不存在";
        }

        String result;
        try {
            result = tool.call(step.description);
        } catch (Exception e) {
            log.error("调用{}工具失败，入参：{}", step.toolName, step.description, e);
            step.setStatus("FAILED");
            result = e.getMessage();
        }

        step.setStatus("COMPLETED");
        return result;
    }

    /**
     * 汇总所有步骤结果，生成最终答案。
     */
    public String synthesizeResults(String task, List<Step> completedSteps) {
        // 实现结果汇总
        // 1. 将所有步骤的结果拼接为上下文
        // 2. 构建汇总 prompt："根据以下步骤结果，回答原始问题：{task}"
        // 3. 调用 chatClient 生成最终答案
        // 【优化】修复 replace 占位符 bug：必须替换 "{task}" 而非 "task"，否则留下残留花括号
        String prompt = """
                根据以下步骤结果，回答原始问题：{task}

                步骤结果:
                {steps}
                """.replace("{task}", task).replace("{steps}", completedSteps.toString());

        return chatClient.prompt().user(prompt).call().content();
    }

    /**
     * 计划步骤 — 内部 DTO。
     */
    @Data
    public static class Step {
        public int stepNumber;
        public String toolName;
        public String description;
        public String status;   // PENDING / COMPLETED / FAILED
        public String result;

        public Step(int stepNumber, String toolName, String description) {
            this.stepNumber = stepNumber;
            this.toolName = toolName;
            this.description = description;
            this.status = "PENDING";
        }
    }
}