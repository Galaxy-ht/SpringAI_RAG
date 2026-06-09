package com.egon.springai_rag.agent.text2sql;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.text2sql")
public class Text2SqlProperties {
    private String schema = "public";
    private List<String> allowedTables = List.of(
            "departments", "employees", "salaries", "projects", "employee_projects"
    );
    private int maxRows = 100;
    private FewShot fewshot = new FewShot();

    @Data
    public static class FewShot {
        private int topK = 3;
    }
}