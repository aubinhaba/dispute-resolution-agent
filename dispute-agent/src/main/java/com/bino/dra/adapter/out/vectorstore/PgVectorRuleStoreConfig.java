package com.bino.dra.adapter.out.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "dra.rag.store", havingValue = "pgvector")
public class PgVectorRuleStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(PgVectorRuleStoreConfig.class);

    @Bean
    // Without this, nothing guarantees Flyway created rule_chunk before the bean runs
    @DependsOnDatabaseInitialization
    public RuleIndex ruleIndex(VectorStore store,
                               JdbcTemplate jdbc,
                               @Value("${spring.ai.vectorstore.pgvector.table-name}") String tableName,
                               @Value("classpath:rules/*.md") Resource[] ruleSheets) {
        // Truncate and rebuild each start: an index derived every time cannot drift from the corpus
        jdbc.execute("TRUNCATE TABLE " + tableName);

        List<Document> chunks = RuleCorpusLoader.load(ruleSheets);
        store.add(chunks);

        log.info("pgvector index rebuilt: {} chunks from {} sheets", chunks.size(), ruleSheets.length);
        return new RuleIndex(chunks.size());
    }

    public record RuleIndex(int chunkCount) {
    }
}
