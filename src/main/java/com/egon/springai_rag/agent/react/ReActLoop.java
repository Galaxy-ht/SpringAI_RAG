package com.egon.springai_rag.agent.react;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct（Reasoning + Acting）循环引擎。
 * <p>
 * ReAct 是 Agent 领域最基础的思考-行动循环模式：
 * <pre>
 *   Thought → Action → Observation → Thought → ... → Final Answer
 * </pre>
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>构建 ReAct 格式的 prompt（含可用工具列表 + 历史轨迹）</li>
 *   <li>调用 LLM 并解析输出中的 Thought/Action/Action Input/Final Answer</li>
 *   <li>根据 Action 查找并执行对应工具</li>
 *   <li>将 Observation 追加到历史轨迹，继续循环</li>
 *   <li>识别终止条件：Final Answer 或达到最大迭代次数</li>
 * </ol>
 * <p>
 * <b>ReAct Prompt 模板（经典格式）：</b>
 * <pre>
 * You are a helpful assistant that can use tools to answer questions.
 *
 * Available tools:
 * - rag_search: 搜索 RAG 知识库中的相关文档
 * - calculator: 执行数学运算
 * - datetime: 获取当前日期和时间
 *
 * Use the following format:
 * Thought: your reasoning about what to do next
 * Action: the tool name to use (or "finish" to return the final answer)
 * Action Input: the input to the tool
 * Observation: the result of the tool (will be filled in by the system)
 * ... (repeat Thought/Action/Action Input/Observation as needed)
 * Thought: I now have enough information to answer
 * Action: finish
 * Final Answer: the final answer to the original question
 *
 * Begin!
 *
 * Question: {task}
 * History:
 * {history}
 * Thought:
 * </pre>
 */
@Slf4j
@Component
public class ReActLoop {

    private final Map<String, ToolCallback> toolMap;
    private final ChatClient chatClient;

    public ReActLoop(List<ToolCallback> tools, ChatClient.Builder chatClientBuilder) {
        this.toolMap = tools.stream()
                .collect(java.util.stream.Collectors.toMap(
                        t -> t.getToolDefinition().name(),
                        t -> t));
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 执行 ReAct 循环。
     *
     * @param task          用户任务
     * @param maxIterations 最大迭代次数
     * @return 最终答案
     */
    public String run(String task, int maxIterations) {
        // 实现 ReAct 循环
        // 1. 构建系统 prompt（含可用工具列表和使用格式）
        // 2. 初始化 history 列表
        // 3. 循环（最多 maxIterations 次）：
        //    a. 构建完整 prompt = 系统 prompt + history + "Thought:"
        //    b. 调用 chatClient.prompt().user(fullPrompt).call().content()
        //    c. 解析 LLM 输出：
        //       - 如果包含 "Action: finish" 或 "Final Answer:"，提取最终答案并返回
        //       - 如果包含 "Action: xxx" 和 "Action Input: yyy"，执行工具
        //    d. 执行工具：
        //       - 从 toolMap 中查找对应工具
        //       - 调用 tool.call(actionInput)
        //       - 将 "Observation: result" 追加到 history
        //    e. 继续循环
        // 4. 如果达到最大迭代次数，返回当前最佳答案

        String prompt = """
                You are a helpful assistant that can use tools to answer questions.
                
                 Available tools:
                 - rag_search: 搜索 RAG 知识库中的相关文档
                 - calculator: 执行数学运算
                 - datetime: 获取当前日期和时间
                 You may only resolve the issue using the tools listed above;
                 you are not permitted to utilize your own search capabilities.
                
                 Use the following format:
                 Thought: your reasoning about what to do next
                 Action: the tool name to use (or "finish" to return the final answer)
                 Action Input: the input to the tool, if it is null, use '{}'
                 Observation: the result of the tool (will be filled in by the system)
                 ... (repeat Thought/Action/Action Input/Observation as needed)
                 Thought: I now have enough information to answer
                 Action: finish
                 Final Answer: the final answer to the original question
                 
                 In once Iteration you just have 1 Action & Action Input;
                 Your final response must strictly adhere to the following format (prefixed with "Final Answer"):
                 ```
                 Final Answer: 现在的时间是 2026 年 6 月 4 日 22 点 22 分 55 秒。
                 ```
                
                 Begin!
                
                 Question: {task}
                 History:
                 {history}
                 Thought:
                """;
        int iteration = 1;
        List<ReActStep> history = new ArrayList<>();

        while (iteration <= maxIterations) {
            String fullPrompt = prompt.replace("{task}",  task).replace("{history}", parseReActStep(history));
            if (iteration == maxIterations) {
                fullPrompt = fullPrompt + "\nThis is the last iteration, please output the final answer directly!";
            }
            String content = chatClient.prompt().user(fullPrompt).call().content();
            ReActStep reActStep = parseOutput(content);
            if (reActStep == null) {
                throw new IllegalArgumentException("Invalid react step");
            }
            if (reActStep.isFinal) {
                history.add(reActStep);
                break;
            }
            if (StringUtils.isNotBlank(reActStep.action) && !reActStep.action.equals("finish")) {
                String observation = executeTool(reActStep.action, reActStep.actionInput);
                reActStep = new ReActStep(reActStep.thought, reActStep.action, reActStep.actionInput, observation, reActStep.finalAnswer, reActStep.isFinal);
            }
            history.add(reActStep);
            iteration++;
        }

        log.info("ReAct执行完成，执行链：");
        log.info(parseReActStep(history));
        return history.getLast().finalAnswer;
    }

    // 【优化】简化字符串拼接逻辑，减少重复的 \n 和 isBlank 判断
    public String parseReActStep(List<ReActStep> reActSteps) {
        StringBuilder sb = new StringBuilder();
        for (ReActStep step : reActSteps) {
            if (StringUtils.isNotBlank(step.thought)) sb.append("\nThought:").append(step.thought).append("\n");
            if (StringUtils.isNotBlank(step.action)) sb.append("Action:").append(step.action).append("\n");
            if (StringUtils.isNotBlank(step.actionInput)) sb.append("Action Input:").append(step.actionInput).append("\n");
            if (StringUtils.isNotBlank(step.observation)) sb.append("Observation:").append(step.observation).append("\n");
            if (StringUtils.isNotBlank(step.finalAnswer)) sb.append("Final Answer:").append(step.finalAnswer).append("\n");
        }
        String message = sb.toString();
        return StringUtils.isBlank(message) ? "no history" : message;
    }

    /**
     * 解析 LLM 输出中的 Action 和 Action Input。
     *
     * @param llmOutput LLM 的原始文本输出
     * @return 解析结果：{action, actionInput, finalAnswer, isFinal}
     */
    public ReActStep parseOutput(String llmOutput) {
        // 实现输出解析
        // 1. 检查是否包含 "Final Answer:" → 提取冒号后的内容，标记 isFinal=true
        // 2. 检查是否包含 "Action: finish" → 同上
        // 3. 检查是否包含 "Action:" 和 "Action Input:" → 提取两个字段
        // 4. 如果只包含 "Thought:" 没有 Action → 继续循环（将 LLM 输出追加到 history）

        if (StringUtils.isBlank(llmOutput)) {
            return null;
        }
        Map<String, String> result = new HashMap<>();

        // 正则匹配：字段名（如 Thought）后跟冒号和可选空白，然后捕获到下一个字段名或字符串结束
        Pattern pattern = Pattern.compile(
                "(?s)(?:^|\\n)(Thought|Action|Action Input|Observation|Final Answer):\\s*(.*?)(?=\\n(?:Thought|Action|Action Input|Observation|Final Answer):|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(llmOutput);
        while (matcher.find()) {
            String field = matcher.group(1);
            String value = matcher.group(2).trim();
            result.put(field, value);
        }

        return new ReActStep(result.get("Thought"), result.get("Action"), result.get("Action Input"), result.get("Observation"),
                result.get("Final Answer"), StringUtils.isNotBlank(result.get("Final Answer")));
    }

    /**
     * 执行工具并返回 Observation。
     */
    public String executeTool(String action, String actionInput) {
        ToolCallback toolCallback = toolMap.get(action);
        String result;
        try {
            result = toolCallback.call(actionInput);
        } catch (Exception e) {
            log.error("调用{}异常，参数{}", action, actionInput, e);
            result = e.getMessage();
        }
        return result;
    }

    /**
     * ReAct 单步解析结果。
     */
    public record ReActStep(
            String thought,
            String action,
            String actionInput,
            String observation,
            String finalAnswer,
            boolean isFinal
    ) {}
}