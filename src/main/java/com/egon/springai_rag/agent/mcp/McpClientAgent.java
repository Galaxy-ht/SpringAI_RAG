package com.egon.springai_rag.agent.mcp;

import com.egon.springai_rag.agent.AbstractAgent;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP Client Agent — 发现并调用远程 MCP Server 工具的智能体。
 * <p>
 * 核心流程：
 * <pre>
 *   用户提问 → 发现远程工具 → 构建 Prompt（含远程工具描述）
 *   → LLM 决定调用哪个工具 → 执行远程工具 → 基于结果生成答案
 * </pre>
 * <p>
 * 与 P5 的 ReActAgent 不同：ReActAgent 调用的是本地 ToolCallback，
 * 而 McpClientAgent 调用的是远程 MCP Server 暴露的工具。
 * <p>
 * <b>你需要实现的内容：</b>
 * <ol>
 *   <li>发现远程工具：遍历所有 McpSyncClient，汇总各 Server 的工具列表</li>
 *   <li>调用远程工具：根据工具名和参数调用指定 MCP Server 的工具</li>
 *   <li>Agent 执行循环：让 LLM 自主选择并调用远程工具</li>
 * </ol>
 * <p>
 * <b>MCP Tool Discovery Prompt 参考：</b>
 * <pre>
 * 你可以使用以下远程 MCP Server 提供的工具：
 *
 * Server: filesystem
 *   - read_file: 读取文件内容 (参数: path)
 *   - list_directory: 列出目录内容 (参数: path)
 *
 * Server: web-search
 *   - search: 搜索网页 (参数: query, maxResults)
 *
 * 用户问题：{task}
 *
 * 请判断是否需要调用远程工具，如果需要，请按以下格式输出：
 * Server: <server名称>
 * Tool: <工具名称>
 * Arguments: <JSON 格式的参数>
 * </pre>
 */
@Slf4j
@Component("mcpClientAgent")
public class McpClientAgent extends AbstractAgent {

    private final List<McpSyncClient> mcpClients;

    public McpClientAgent(ChatClient.Builder chatClientBuilder,
                          List<ToolCallback> tools,
                          List<McpSyncClient> mcpClients) {
        super(chatClientBuilder, tools, "MCP-Client-Agent");
        this.mcpClients = mcpClients;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute(String task) {
        // 实现 MCP Client Agent 执行逻辑
        // Step 1: 检查是否有可用的远程 MCP Server
        //   if (mcpClients.isEmpty()) { return "没有可用的远程 MCP Server 连接"; }
        //
        // Step 2: 发现所有远程工具
        //   Map<String, List<McpSchema.Tool>> allRemoteTools = discoverTools();
        //   if (allRemoteTools.isEmpty()) { return "远程 MCP Server 没有暴露任何工具"; }
        //
        // Step 3: 构建 prompt（含远程工具描述 + 用户任务）
        //   String prompt = buildToolSelectionPrompt(task, allRemoteTools);
        //
        // Step 4: 让 LLM 决定调用哪个工具
        //   String llmResponse = chatClient.prompt().user(prompt).call().content();
        //
        // Step 5: 解析 LLM 输出，提取 serverName, toolName, arguments
        //   提示：可以用正则匹配 "Server:"、"Tool:"、"Arguments:" 字段
        //
        // Step 6: 执行远程工具
        //   String toolResult = executeMcpTool(serverName, toolName, arguments);
        //
        // Step 7: 基于工具结果生成最终答案
        //   String finalPrompt = "基于以下远程工具结果，回答用户问题...";
        //   return chatClient.prompt().user(finalPrompt).call().content();
        if (mcpClients.isEmpty()) { return "没有可用的远程 MCP Server 连接"; }

        Map<String, List<McpSchema.Tool>> allRemoteTools = discoverTools();
        if (allRemoteTools.isEmpty()) { return "远程 MCP Server 没有暴露任何工具"; }

        String prompt = buildToolSelectionPrompt(task, allRemoteTools);
        String llmResponse = chatClient.prompt().user(prompt).call().content();

        if (StringUtils.isBlank(llmResponse)) {
            throw new IllegalStateException("LLM无响应");
        }

        Map<String, String> tool = new HashMap<>();

        // 正则匹配：字段名（如 Thought）后跟冒号和可选空白，然后捕获到下一个字段名或字符串结束
        Pattern pattern = Pattern.compile(
                "(?s)(?:^|\\n)(Server|Tool|Arguments):\\s*(.*?)(?=\\n(?:Server|Tool|Arguments):|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(llmResponse);
        while (matcher.find()) {
            String field = matcher.group(1);
            String value = matcher.group(2).trim();
            tool.put(field, value);
        }

        if (tool.isEmpty()) {
            return llmResponse;
        }

        String toolResult = executeMcpTool(tool.get("Server"), tool.get("Tool"), objectMapper.readValue(tool.get("Arguments"), new TypeReference<Map<String, Object>>() {}));

        String finalPrompt = """
                基于以下远程工具结果，回答用户问题: {task}
                远程工具: {tool}
                远程工具结果: {toolResult}
                """
                .replace("{task}", task)
                .replace("{tool}", tool.toString())
                .replace("{toolResult}", toolResult);

        return chatClient.prompt().user(finalPrompt).call().content();
    }

    /**
     * 发现所有远程 MCP Server 暴露的工具。
     * <p>
     * 遍历所有已连接的 McpSyncClient，调用 listTools() 获取工具列表，
     * 按 Server 名称分组返回。
     *
     * @return Map<Server名称, List<工具>>
     */
    public Map<String, List<McpSchema.Tool>> discoverTools() {
        // 实现远程工具发现逻辑
        // Step 1: 创建结果 Map
        //   Map<String, List<McpSchema.Tool>> allTools = new HashMap<>();
        //
        // Step 2: 遍历所有 mcpClients
        //   for (McpSyncClient client : mcpClients) {
        //       try {
        //           // 获取 Server 信息
        //           String serverName = client.getServerInfo().name();
        //           // 获取工具列表
        //           McpSchema.ListToolsResult result = client.listTools();
        //           allTools.put(serverName, result.tools());
        //           log.info("从 {} 发现 {} 个工具", serverName, result.tools().size());
        //       } catch (Exception e) {
        //           log.error("发现工具失败: {}", e.getMessage());
        //       }
        //   }
        //
        // Step 3: 返回结果
        //   return allTools;

        Map<String, List<McpSchema.Tool>> allTools = new HashMap<>();
        for (McpSyncClient client : mcpClients) {
            try {
                // 获取 Server 信息
                String serverName = client.getServerInfo().name();
                // 获取工具列表
                McpSchema.ListToolsResult result = client.listTools();
                allTools.put(serverName, result.tools());
                log.info("从 {} 发现 {} 个工具", serverName, result.tools().size());
            } catch (Exception e) {
                log.error("发现工具失败: {}", e.getMessage());
            }
        }
        return allTools;
    }

    /**
     * 调用指定 MCP Server 的远程工具。
     * <p>
     * 通过 Server 名称找到对应的 McpSyncClient，构造 CallToolRequest 并执行。
     *
     * @param serverName 远程 MCP Server 名称
     * @param toolName   工具名称
     * @param arguments  工具参数（JSON 格式的 Map）
     * @return 工具执行结果文本
     */
    public String executeMcpTool(String serverName, String toolName, Map<String, Object> arguments) {
        // 实现远程工具调用逻辑
        // Step 1: 根据 serverName 找到对应的 McpSyncClient
        //   McpSyncClient targetClient = null;
        //   for (McpSyncClient client : mcpClients) {
        //       if (client.getServerInfo().name().equals(serverName)) {
        //           targetClient = client;
        //           break;
        //       }
        //   }
        //   if (targetClient == null) { return "未找到 Server: " + serverName; }
        //
        // Step 2: 构造 CallToolRequest
        //   McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
        //       toolName,
        //       arguments != null ? arguments : Map.of()
        //   );
        //
        // Step 3: 调用远程工具
        //   McpSchema.CallToolResult result = targetClient.callTool(request);
        //
        // Step 4: 提取结果文本
        //   使用 StringBuilder 拼接所有 TextContent
        //   for (McpSchema.Content content : result.content()) {
        //       if (content instanceof McpSchema.TextContent text) {
        //           sb.append(text.text());
        //       }
        //   }
        //
        // Step 5: 返回结果文本
        //   return sb.toString();

        McpSyncClient targetClient = null;

        for (McpSyncClient client : mcpClients) {
            if (client.getServerInfo().name().equals(serverName)) {
                targetClient = client;
                break;
            }
        }

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                toolName,
                arguments != null ? arguments : Map.of()
        );

        if (targetClient == null) { return "未找到 Server: " + serverName; }

        McpSchema.CallToolResult result = targetClient.callTool(request);

        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent text) {
                sb.append(text.text());
            }
        }

        return sb.toString();
    }

    /**
     * 构建工具选择 Prompt，让 LLM 根据任务选择合适的远程工具。
     * <p>
     * 提示：可以复用此方法，也可自行设计更好的 prompt。
     */
    private String buildToolSelectionPrompt(String task, Map<String, List<McpSchema.Tool>> allRemoteTools) {
        StringBuilder toolsDesc = new StringBuilder();
        for (var entry : allRemoteTools.entrySet()) {
            toolsDesc.append("Server: ").append(entry.getKey()).append("\n");
            for (McpSchema.Tool tool : entry.getValue()) {
                toolsDesc.append("  - ").append(tool.name()).append(": ")
                        .append(tool.description() != null ? tool.description() : "无描述")
                        .append("\n");
            }
            toolsDesc.append("\n");
        }

        return """
                你可以使用以下远程 MCP Server 提供的工具：

                %s
                用户问题：%s

                请判断是否需要调用远程工具，如果需要，请按以下格式输出：
                Server: <server名称>
                Tool: <工具名称>
                Arguments: <JSON 格式的参数>

                如果不需要调用远程工具，请直接回答问题。
                """.formatted(toolsDesc.toString(), task);
    }
}