package com.bino.dra.adapter.out.agent;

import com.bino.dra.adapter.out.vectorstore.RuleCorpusLoader;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmComplianceAgentTest {

    @Test
    void a_cited_passage_carries_its_corpus_identifier_as_a_prefix() {
        String cited = LlmComplianceAgent.cite(realisticChunk());

        assertThat(cited).startsWith("[visa-10.4#liability-shift]");
        assertThat(cited).contains("Fraud - Card-Absent Environment");
        assertThat(cited).contains("Liability shift");
    }

    @Test
    void the_citation_strips_the_provenance_header_added_at_indexing_time() {
        String cited = LlmComplianceAgent.cite(realisticChunk());

        assertThat(cited).doesNotContain("VISA reason code 10.4 -");
        assertThat(cited).contains("Where the transaction was successfully authenticated");
    }

    @Test
    void the_citation_fits_on_a_single_line() {
        assertThat(LlmComplianceAgent.cite(realisticChunk())).doesNotContain("\n");
    }

    @Test
    void a_chunk_of_unexpected_shape_is_cited_whole_rather_than_lost() {
        Document offFormat = Document.builder()
                .id("visa-13.1#scope")
                .text("Some text without a provenance header.")
                .metadata(Map.of(
                        RuleCorpusLoader.META_TITLE, "Merchandise or Services Not Received",
                        RuleCorpusLoader.META_SECTION, "Scope"))
                .build();

        assertThat(LlmComplianceAgent.cite(offFormat))
                .contains("Some text without a provenance header.");
    }

    @Test
    void the_network_filter_accepts_the_dispute_network_and_cross_cutting_rules() {
        String filter = LlmComplianceAgent.networkFilter(Network.VISA).toString();

        assertThat(filter).contains("VISA").contains(RuleCorpusLoader.ANY).contains("OR");
    }

    private static Document realisticChunk() {
        return Document.builder()
                .id("visa-10.4#liability-shift")
                .text("""
                        VISA reason code 10.4 - Fraud - Card-Absent Environment
                        Liability shift

                        Where the transaction was successfully authenticated through 3-D Secure,
                        the fraud liability moves to the issuer.""")
                .metadata(Map.of(
                        RuleCorpusLoader.META_RULE_ID, "visa-10.4",
                        RuleCorpusLoader.META_NETWORK, "VISA",
                        RuleCorpusLoader.META_REASON_CODE, "10.4",
                        RuleCorpusLoader.META_TITLE, "Fraud - Card-Absent Environment",
                        RuleCorpusLoader.META_SECTION, "Liability shift"))
                .build();
    }
}
