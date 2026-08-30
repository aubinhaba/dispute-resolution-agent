package com.bino.dra.adapter.out.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class RuleVectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(RuleVectorStoreConfig.class);

    @Bean
    @ConditionalOnProperty(name = "dra.rag.store", havingValue = "simple")
    public VectorStore ruleVectorStore(EmbeddingModel embeddingModel,
                                       @Value(RuleCorpusLoader.CORPUS_LOCATION) Resource[] ruleSheets) {
        List<Document> chunks = RuleCorpusLoader.load(ruleSheets);

        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        store.add(chunks);

        log.info("Rule index built: {} chunks from {} sheets", chunks.size(), ruleSheets.length);
        return store;
    }
}
