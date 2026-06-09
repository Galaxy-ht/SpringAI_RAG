package com.egon.springai_rag.agent.text2sql;

import com.egon.springai_rag.agent.text2sql.dto.Text2SqlDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SQL 校验器 — 基于 JSqlParser AST 的安全校验。
 * <p>
 * 三层防线：
 * <ol>
 *   <li>AST 解析 — 确保 SQL 语法合法，且仅为 SELECT 语句</li>
 *   <li>表名白名单 — 从 AST 中提取所有表名（含子查询、CTE、JOIN），逐一校验</li>
 *   <li>执行层保护 — JdbcTemplate 使用 PreparedStatement，杜绝注入</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlValidator {

    private final SchemaManager schemaManager;

    /**
     * 验证 SQL 是否安全可执行。
     * <p>
     * 使用 JSqlParser 构建 AST，而非正则匹配。AST 方式天然处理了：
     * <ul>
     *   <li>子查询中的表名提取</li>
     *   <li>CTE (WITH 子句) 中的表名提取</li>
     *   <li>UNION 多个 SELECT 中的表名提取</li>
     *   <li>字符串字面量中的关键字（不会被误判）</li>
     *   <li>注释混淆的 SQL（parser 会先标准化）</li>
     * </ul>
     */
    public Text2SqlDtos.ValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return new Text2SqlDtos.ValidationResult(false, "SQL 为空或空白", Collections.emptyList());
        }

        // Step 1: 解析为 AST — 非 SELECT 语句（INSERT/UPDATE/DELETE/DROP 等）会在此处被识别
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            return new Text2SqlDtos.ValidationResult(false, "SQL 语法解析失败: " + e.getMessage(),
                    Collections.emptyList());
        }

        // Step 2: 仅允许 SELECT 语句（包含 WITH/CTE 开头的 SELECT）
        if (!(statement instanceof Select)) {
            return new Text2SqlDtos.ValidationResult(false,
                    "仅允许 SELECT 查询，不允许 " + statement.getClass().getSimpleName(), Collections.emptyList());
        }

        // Step 3: 从 AST 中提取所有表名（自动处理子查询、CTE、JOIN、UNION）
        TablesNamesFinder finder = new TablesNamesFinder();
        Set<String> allTableNames = new HashSet<>(finder.getTableList(statement));

        // Step 4: 验证每个表名都在白名单中
        var blockedReasons = new ArrayList<String>();
        for (String tableName : allTableNames) {
            if (!schemaManager.isTableAllowed(tableName)) {
                blockedReasons.add("表名 " + tableName + " 不在白名单中");
            }
        }
        if (!blockedReasons.isEmpty()) {
            return new Text2SqlDtos.ValidationResult(false, "SQL 包含不允许的表名", blockedReasons);
        }

        return new Text2SqlDtos.ValidationResult(true, "SQL 通过校验", Collections.emptyList());
    }

    /**
     * 从 SQL 中提取所有涉及的表名（用于调试和日志）。
     */
    public Set<String> extractTableNames(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            return new HashSet<>(new TablesNamesFinder().getTableList(statement));
        } catch (Exception e) {
            log.warn("Failed to extract table names: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 检测 SQL 中是否包含潜在的注入模式。
     * <p>
     * 注意：在 AST 校验通过后，此方法仅作为辅助检测层。
     * 主要防护由 AST 解析器 + JdbcTemplate PreparedStatement 提供。
     */
    public boolean hasInjectionPattern(String sql) {
        // AST 校验已经能拦截绝大多数注入，此方法保留作为快速预检
        // 检测多语句（分号分隔）
        if (sql == null || sql.isBlank()) return false;
        String stripped = sql.strip();
        // 检测堆叠查询：去掉字符串字面量后检查分号
        String noLiterals = stripStringLiterals(stripped);
        int semicolons = 0;
        for (char c : noLiterals.toCharArray()) {
            if (c == ';') semicolons++;
        }
        return semicolons > 1 || (semicolons == 1 && !stripped.endsWith(";"));
    }

    private String stripStringLiterals(String sql) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!inString && (c == '\'' || c == '"')) {
                inString = true;
                stringChar = c;
                result.append('?');
                continue;
            }
            if (inString) {
                if (c == stringChar && (i + 1 < sql.length() && sql.charAt(i + 1) == stringChar)) {
                    i++; // 转义的引号
                    continue;
                }
                if (c == stringChar) {
                    inString = false;
                    result.append('?');
                    continue;
                }
                continue; // 跳过字符串内容
            }
            result.append(c);
        }
        return result.toString();
    }
}