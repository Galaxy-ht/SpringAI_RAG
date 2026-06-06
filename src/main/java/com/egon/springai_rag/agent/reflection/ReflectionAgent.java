package com.egon.springai_rag.agent.reflection;

import com.egon.springai_rag.agent.AbstractAgent;
import com.egon.springai_rag.agent.WorkerAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reflection Agent — 具备自我反思和纠错能力的智能体。
 * <p>
 * 核心流程：
 * <pre>
 *   用户提问 → 生成初始答案 → 自我批评(Critique)
 *   → 发现不足 → 修正答案 → 再次批评 → ... → 最终答案
 * </pre>
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>生成初始答案（调用 ChatClient + 工具）</li>
 *   <li>构建 Critique prompt：让 LLM 评估自己的答案质量</li>
 *   <li>解析 Critique 结果，判断是否需要修正</li>
 *   <li>如果需要修正，基于 Critique 建议重新生成答案</li>
 *   <li>循环直到答案满意或达到最大重试次数</li>
 * </ol>
 * <p>
 * <b>Critique Prompt 参考：</b>
 * <pre>
 * 请评估以下答案的质量：
 *
 * 问题：{task}
 * 答案：{currentAnswer}
 *
 * 从以下维度评估（1-5分）：
 * 1. 准确性：答案是否与已知事实一致？
 * 2. 完整性：是否覆盖了问题的所有方面？
 * 3. 清晰度：表达是否清晰易懂？
 * 4. 相关性：是否直接回答了问题？
 *
 * 如果总分低于 4 分，请指出具体问题并给出改进建议。
 * 输出格式：
 * Score: X/5
 * Issues: [问题列表]
 * NeedsRevision: true/false
 *
 * Suggestions: [改进建议]
 * </pre>
 */
@Slf4j
@Component("reflectionAgent")
@WorkerAgent
public class ReflectionAgent extends AbstractAgent {

    @Value("${app.agent.reflection.max-retries:3}")
    private int maxRetries;

    public ReflectionAgent(ChatClient.Builder chatClientBuilder,
                           List<ToolCallback> tools) {
        super(chatClientBuilder, tools, "Reflection-Agent");
    }

    @Override
    public String execute(String task) {
        // 实现 Reflection Agent 执行逻辑
        // 1. 生成初始答案：
        //    String answer = chatClient.prompt().user(task).call().content();
        // 2. 自我批评循环（最多 maxRetries 次）：
        //    a. 构建 critique prompt
        //    b. String critique = critiqueClient.prompt().user(critiquePrompt).call().content();
        //    c. 解析 critique，判断是否 needsRevision
        //    d. 如果不需要修正 → 返回当前答案
        //    e. 如果需要修正 → 将 critique 建议作为上下文，重新生成答案
        // 3. 返回最佳答案

        String answer = chatClient.prompt().user(task).call().content();
        if (StringUtils.isBlank(answer)) {
            throw new IllegalStateException("请稍后再试");
        }
        int retries = 0;

        String prompt = """
                请根据当前用户问题、当前答案与答案修改建议，重新生成优化答案
                用户问题: {task}
                当前答案: {answer}
                修改建议: {critique}
                """;

        while (true) {
            if (retries >= maxRetries) {
                return answer;
            }
            log.info("第{}轮答案：{}", retries, answer);

            Map<String, String> critique = critique(task, answer);
            log.info("第{}轮评估：{}",retries, critique);
            if (!CollectionUtils.isEmpty(critique) && StringUtils.isNotBlank(critique.get("score"))) {
                if ("false".equals(critique.get("NeedsRevision"))) {
                    return answer;
                }
            }

            String fullPrompt = prompt.replace("{task}", task).replace("{answer}", answer).replace("{critique}", parseCritique(critique));
            String answerTmp = chatClient.prompt().user(fullPrompt).call().content();
            if (StringUtils.isBlank(answerTmp)) {
                log.error("调用模型失败，返回上一轮答案");
                return answer;
            }
            answer = answerTmp;

            retries++;
        }
    }

    private String parseCritique(Map<String, String> critique) {
        StringBuilder sb = new StringBuilder();
        critique.forEach((key, value) ->
            sb.append(key).append("=").append(value).append("\n")
        );
        return sb.toString();
    }

    /**
     * 对当前答案进行自我批评。
     *
     * @param task          原始问题
     * @param currentAnswer 当前答案
     * @return 批评结果（包含评分、问题列表、是否需要修正）
     */
    public Map<String, String> critique(String task, String currentAnswer) {
        // 实现 Critique 生成
        // 1. 构建 critique prompt（参考类注释中的模板）
        // 2. 调用 critiqueClient 获取评估结果
        // 3. 返回评估文本
        String critiquePrompt = """
                 请评估以下答案的质量：
                
                 问题：{task}
                 答案：{currentAnswer}
                
                 从以下维度评估（1-5分）：
                 1. 准确性：答案是否与已知事实一致？
                 2. 完整性：是否覆盖了问题的所有方面？
                 3. 清晰度：表达是否清晰易懂？
                 4. 相关性：是否直接回答了问题？
                
                 如果总分低于 4 分，请指出具体问题并给出改进建议。
                 输出格式(严格遵守【key: value】格式，不得自行编造key)：
                 Score: X/5
                 Issues: [问题列表]
                 NeedsRevision: true/false
                 Suggestions: [改进建议]
                """;
        String fullPrompt = critiquePrompt.replace("{task}", task).replace("{currentAnswer}", currentAnswer);
        String content = chatClient.prompt().user(fullPrompt).call().content();
        log.info("critique生成：{}", content);
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("请稍后再试");
        }

        Map<String, String> result = new HashMap<>();

        // 正则匹配
        Pattern pattern = Pattern.compile(
                "(?s)(?:^|\\n)(Score|Issues|NeedsRevision|Suggestions):\\s*(.*?)(?=\\n(?:Score|Issues|NeedsRevision|Suggestions):|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String field = matcher.group(1);
            String value = matcher.group(2).trim();
            result.put(field, value);
        }

        return result;
    }
}