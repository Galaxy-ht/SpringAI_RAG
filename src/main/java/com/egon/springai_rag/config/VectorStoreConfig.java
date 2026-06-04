package com.egon.springai_rag.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    // ── pgvector: wrap the auto-configured bean ──

    @Bean("pgVectorStore")
    @Lazy
    public VectorStore pgVectorStore(@Qualifier("vectorStore") VectorStore autoConfigPgVectorStore) {
        return autoConfigPgVectorStore;
    }

    // ── Qdrant ──

    @Bean(destroyMethod = "close")
    @Lazy
    @ConditionalOnProperty(name = "app.vector-store.qdrant.enabled", havingValue = "true", matchIfMissing = true)
    public QdrantClient qdrantClient(
            @Value("${spring.ai.vectorstore.qdrant.host:localhost}") String host,
            @Value("${spring.ai.vectorstore.qdrant.port:6334}") int port,
            @Value("${spring.ai.vectorstore.qdrant.use-tls:false}") boolean useTls) {
        return new QdrantClient(QdrantGrpcClient.newBuilder(host, port, useTls).build());
    }

    @Bean("qdrantVectorStore")
    @Lazy
    @ConditionalOnProperty(name = "app.vector-store.qdrant.enabled", havingValue = "true", matchIfMissing = true)
    public VectorStore qdrantVectorStore(
            QdrantClient qdrantClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.qdrant.collection-name:spring_ai_rag}") String collectionName) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();
    }

    // ── Milvus ──

    @Bean(destroyMethod = "close")
    @Lazy
    @ConditionalOnProperty(name = "app.vector-store.milvus.enabled", havingValue = "true")
    public MilvusServiceClient milvusClient(
            @Value("${spring.ai.vectorstore.milvus.host:localhost}") String host,
            @Value("${spring.ai.vectorstore.milvus.port:19530}") int port,
            @Value("${spring.ai.vectorstore.milvus.database:default}") String database,
            @Value("${spring.ai.vectorstore.milvus.username:}") String username,
            @Value("${spring.ai.vectorstore.milvus.password:}") String password) {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withDatabaseName(database);
        if (!username.isEmpty()) {
            builder.withAuthorization(username, password);
        }
        return new MilvusServiceClient(builder.build());
    }

    @Bean("milvusVectorStore")
    @Lazy
    @ConditionalOnProperty(name = "app.vector-store.milvus.enabled", havingValue = "true")
    public VectorStore milvusVectorStore(
            MilvusServiceClient milvusClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.milvus.collection-name:spring_ai_rag}") String collectionName) {
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .collectionName(collectionName)
                .metricType(MetricType.COSINE)
                .indexType(IndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    // ── Elasticsearch (requires spring-boot-starter-data-elasticsearch Rest5Client) ──

    @Bean("elasticsearchVectorStore")
    @Lazy
    @ConditionalOnProperty(name = "app.vector-store.elasticsearch.enabled", havingValue = "true")
    public VectorStore elasticsearchVectorStore(
            co.elastic.clients.transport.rest5_client.low_level.Rest5Client rest5Client,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.elasticsearch.index-name:spring_ai_rag}") String indexName) {
        return org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore
                .builder(rest5Client, embeddingModel)
                .initializeSchema(true)
                .build();
    }

    // ── DelegatingVectorStore (@Primary) ──

    @Bean
    @Primary
    public DelegatingVectorStore delegatingVectorStore(
            ApplicationContext context,
            @Value("${app.vector-store.active:pgvector}") String active) {

        Map<String, VectorStore> stores = new LinkedHashMap<>();

        if (context.containsBean("pgVectorStore")) {
            stores.put("pgvector", tryGetBean(context, "pgVectorStore"));
        }
        if (context.containsBean("vectorStore")) {
            stores.put("pgvector", tryGetBean(context, "vectorStore"));
        }
        if (context.containsBean("qdrantVectorStore")) {
            stores.put("qdrant", tryGetBean(context, "qdrantVectorStore"));
        }
        if (context.containsBean("milvusVectorStore")) {
            stores.put("milvus", tryGetBean(context, "milvusVectorStore"));
        }
        if (context.containsBean("elasticsearchVectorStore")) {
            stores.put("elasticsearch", tryGetBean(context, "elasticsearchVectorStore"));
        }

        stores.values().removeIf(v -> v == null);

        if (stores.isEmpty()) {
            throw new IllegalStateException("No vector store available. Please check your configuration.");
        }

        String resolvedActive = stores.containsKey(active) ? active : stores.keySet().iterator().next();
        if (!resolvedActive.equals(active)) {
            log.warn("配置的活跃向量库 '{}' 不可用，已回退到 '{}'", active, resolvedActive);
        }

        return new DelegatingVectorStore(stores, resolvedActive);
    }

    private VectorStore tryGetBean(ApplicationContext context, String beanName) {
        try {
            return context.getBean(beanName, VectorStore.class);
        } catch (Exception e) {
            log.warn("向量库 {} 不可用，已跳过: {}", beanName, e.getMessage());
            return null;
        }
    }
}