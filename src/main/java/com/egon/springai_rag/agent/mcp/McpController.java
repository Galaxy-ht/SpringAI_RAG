package com.egon.springai_rag.agent.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 协议 REST 控制器。
 * <p>
 * 提供 MCP Server 信息查询和 MCP Client 远程工具调用端点。
 *
 * <h3>端点说明</h3>
 * <ul>
 *   <li>GET  /api/mcp/server/info — MCP Server 状态、暴露的工具列表</li>
 *   <li>GET  /api/mcp/client/tools — 查看远程 MCP Server 暴露的工具</li>
 *   <li>POST /api/mcp/client/execute — 调用远程 MCP 工具</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final List<ToolCallback> localTools;
    private final List<McpSyncClient> mcpClients;

    public McpController(List<ToolCallback> localTools,
                         List<McpSyncClient> mcpClients) {
        this.localTools = localTools;
        this.mcpClients = mcpClients;
    }

    /**
     * 查看 MCP Server 信息：暴露了哪些工具、配置状态等。
     */
    @GetMapping("/server/info")
    public Map<String, Object> serverInfo() {
        List<Map<String, Object>> tools = localTools.stream()
                .map(t -> {
                    var def = t.getToolDefinition();
                    return Map.<String, Object>of(
                            "name", def.name(),
                            "description", def.description()
                    );
                })
                .toList();

        return Map.of(
                "status", "active",
                "exposedTools", tools,
                "toolCount", tools.size()
        );
    }

    /**
     * 查看所有已连接的远程 MCP Server 暴露的工具列表。
     */
    @GetMapping("/client/tools")
    public Map<String, Object> listRemoteTools() {
        if (mcpClients.isEmpty()) {
            return Map.of(
                    "connected", false,
                    "message", "没有配置远程 MCP Server 连接。请在 application.yml 中配置 spring.ai.mcp.client.streamable-http.connections",
                    "servers", List.of()
            );
        }

        List<Map<String, Object>> servers = new ArrayList<>();
        for (McpSyncClient client : mcpClients) {
            Map<String, Object> serverInfo = new HashMap<>();
            try {
                McpSchema.Implementation info = client.getServerInfo();
                serverInfo.put("server", info.name() + " v" + info.version());
                serverInfo.put("initialized", client.isInitialized());

                McpSchema.ListToolsResult toolsResult = client.listTools();
                List<Map<String, Object>> toolList = new ArrayList<>();
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    toolList.add(Map.of(
                            "name", tool.name(),
                            "description", tool.description() != null ? tool.description() : "无描述"
                    ));
                }
                serverInfo.put("tools", toolList);
                serverInfo.put("toolCount", toolList.size());
            } catch (Exception e) {
                log.error("获取远程工具列表失败: {}", e.getMessage());
                serverInfo.put("error", "获取工具列表失败，请检查远程 MCP Server 连接状态");
            }
            servers.add(serverInfo);
        }

        return Map.of(
                "connected", true,
                "serverCount", mcpClients.size(),
                "servers", servers
        );
    }

    /**
     * 调用远程 MCP 工具。
     * <p>
     * 请求体格式：{"server": "secure-filesystem-server v0.2.0", "tool": "read_text_file", "arguments": {"key": "value"}}
     */
    @PostMapping("/client/execute")
    public Map<String, Object> executeRemoteTool(@RequestBody McpExecuteRequest request) {
        // 注意：这是一个学习项目，未集成 Spring Security。
        // 生产环境中应添加授权检查（如 @PreAuthorize 或自定义 AuthorizationManager），
        // 验证调用者是否有权限执行远程 MCP 工具。
        if (mcpClients.isEmpty()) {
            return Map.of("error", "没有可用的远程 MCP Server 连接");
        }

        int serverIndex = request.server != null ? request.server : 0;
        if (serverIndex < 0 || serverIndex >= mcpClients.size()) {
            return Map.of("error", "无效的 Server 索引: " + serverIndex + "，可用范围: 0-" + (mcpClients.size() - 1));
        }

        McpSyncClient client = mcpClients.get(serverIndex);
        log.info("MCP 远程工具调用请求: server={}, tool={}, args={}", client.getServerInfo().name(), request.tool, request.arguments);
        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(request.tool, request.arguments != null ? request.arguments : Map.of())
            );

            List<String> content = new ArrayList<>();
            for (McpSchema.Content item : result.content()) {
                if (item instanceof McpSchema.TextContent text) {
                    content.add(text.text());
                } else {
                    content.add(item.toString());
                }
            }

            return Map.of(
                    "server", client.getServerInfo().name(),
                    "tool", request.tool,
                    "result", content,
                    "isError", result.isError() != null && result.isError()
            );
        } catch (Exception e) {
            log.error("MCP 工具调用失败: tool={}, server={}", request.tool, client.getServerInfo().name(), e);
            return Map.of(
                    "server", client.getServerInfo().name(),
                    "tool", request.tool,
                    "error", "工具调用失败: " + e.getMessage()
            );
        }
    }

    /**
     * MCP 工具调用请求 DTO。
     */
    public record McpExecuteRequest(
            Integer server,
            String tool,
            Map<String, Object> arguments
    ) {}
}