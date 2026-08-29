package com.bino.dra.adapter.out.vectorstore;

import com.bino.dra.application.port.out.RuleRetriever;
import com.bino.dra.domain.model.Network;
import com.bino.dra.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// What matters is what did NOT change for this to pass: agent, reranker and port (see ADR-0005)
@SpringBootTest(properties = {
        "spring.ai.anthropic.api-key=not-used-by-this-test",
        "dra.rag.store=pgvector",
        "dra.persistence=jdbc"
})
@Import(PostgresTestcontainer.class)
class PgVectorRuleRetrievalIT {

    private static final int EXPECTED_TOTAL_CHUNKS = 90;

    @Autowired
    private VectorStore store;

    @Autowired
    private RuleRetriever retriever;

    @Autowired
    private PgVectorRuleStoreConfig.RuleIndex index;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void the_wired_store_is_pgvector_and_not_the_in_memory_one() {
        // Without this, a condition mistake would run everything else green on SimpleVectorStore
        assertThat(store.getClass().getSimpleName()).isEqualTo("PgVectorStore");
    }

    @Test
    void the_whole_corpus_is_indexed_in_postgres_without_duplicates() {
        // Counted in SQL: asking the object under test to validate itself proves nothing
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM rule_chunk", Integer.class);

        assertThat(rows).isEqualTo(EXPECTED_TOTAL_CHUNKS);
        assertThat(index.chunkCount()).isEqualTo(EXPECTED_TOTAL_CHUNKS);
    }

    @Test
    void the_RuleRetriever_port_returns_attestable_passages_from_pgvector() {
        List<String> passages = retriever.retrieveRulePassages("10.4", Network.VISA);

        assertThat(passages).isNotEmpty();
        assertThat(passages).allSatisfy(p -> assertThat(p).startsWith("["));
        assertThat(passages).anySatisfy(p -> assertThat(p).contains("visa-10.4"));
    }

    @Test
    void the_network_filter_still_applies_once_translated_into_SQL() {
        List<String> mastercard = retriever.retrieveRulePassages("4837", Network.MASTERCARD);

        assertThat(mastercard).isNotEmpty();
        assertThat(mastercard).noneSatisfy(p -> assertThat(p).contains("[visa-"));
    }
}
