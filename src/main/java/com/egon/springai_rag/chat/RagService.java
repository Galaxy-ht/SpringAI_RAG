package com.egon.springai_rag.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final String PROMPT_TEMPLATE = """
            以下是从知识库中检索到的相关文档：
            ---
            {context}
            ---
            请根据以上文档内容回答用户的问题。如果文档中没有相关信息，请说明你没有找到相关内容，不要编造答案。
            用户问题：{question}
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public String query(String question) {
        List<Document> documents = vectorStore.similaritySearch(question);
        String context = documents.stream()
                .map(doc -> doc.getText() + " [来源: " + doc.getMetadata().get("source") + "]")
                .collect(Collectors.joining("\n\n"));

        PromptTemplate promptTemplate = new PromptTemplate(PROMPT_TEMPLATE);
        var prompt = promptTemplate.create(Map.of("context", context, "question", question));

        return chatClient.prompt(prompt).call().content();
    }
}