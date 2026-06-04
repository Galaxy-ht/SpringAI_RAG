package com.egon.springai_rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration;

@SpringBootApplication(exclude = {
    OpenAiEmbeddingAutoConfiguration.class,
    MilvusVectorStoreAutoConfiguration.class,
    QdrantVectorStoreAutoConfiguration.class,
    ElasticsearchVectorStoreAutoConfiguration.class
})
public class SpringAiRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiRagApplication.class, args);
    }

}
