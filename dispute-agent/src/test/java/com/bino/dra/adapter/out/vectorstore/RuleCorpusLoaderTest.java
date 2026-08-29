package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleCorpusLoaderTest {

    private static final String MINIMAL_SHEET = """
            ---
            ruleId: visa-13.1
            network: VISA
            reasonCode: "13.1"
            title: Merchandise or Services Not Received
            ---

            ## Scope

            Applies when the cardholder agreed to the purchase but nothing arrived.

            ## Time limits

            The issuer must raise the dispute within 120 calendar days.
            """;

    @Test
    void each_section_becomes_one_chunk() {
        assertThat(RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md")).hasSize(2);
    }

    @Test
    void audit_id_is_readable_and_derived_from_the_section_title() {
        List<Document> chunks = RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md");

        // This id IS the citation that ends up in DisputeDecision.citedRulePassages
        assertThat(chunks).extracting(RuleCorpusLoader::chunkId)
                .containsExactly("visa-13.1#scope", "visa-13.1#time-limits");
    }

    @Test
    void technical_id_is_a_deterministic_uuid_derived_from_the_audit_id() {
        List<Document> first = RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md");
        List<Document> second = RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md");

        // Deterministic, or reindexing at startup would orphan every archived citation (ADR-0005)
        assertThat(first).allSatisfy(chunk ->
                assertThatCode(() -> UUID.fromString(chunk.getId())).doesNotThrowAnyException());
        assertThat(first).extracting(Document::getId)
                .containsExactlyElementsOf(second.stream().map(Document::getId).toList());
    }

    @Test
    void indexed_text_carries_the_network_the_reason_code_and_the_sheet_title() {
        List<Document> chunks = RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md");

        // The body contains neither "Visa" nor "13.1": without enrichment it is unretrievable
        assertThat(chunks.get(1).getText())
                .contains("VISA reason code 13.1")
                .contains("Merchandise or Services Not Received")
                .contains("Time limits")
                .contains("120 calendar days");
    }

    @Test
    void metadata_supports_filtering_and_reranking() {
        Document scope = RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md").get(0);

        assertThat(scope.getMetadata())
                .containsEntry(RuleCorpusLoader.META_RULE_ID, "visa-13.1")
                .containsEntry(RuleCorpusLoader.META_NETWORK, "VISA")
                .containsEntry(RuleCorpusLoader.META_REASON_CODE, "13.1")
                .containsEntry(RuleCorpusLoader.META_SECTION, "Scope");
    }

    @Test
    void a_cross_cutting_sheet_does_not_announce_a_reason_code() {
        String crossCutting = """
                ---
                ruleId: shared-3ds
                network: ANY
                reasonCode: ANY
                title: 3-D Secure Authentication and Fraud Liability Shift
                ---

                ## Principle

                Successful authentication moves fraud liability to the issuer.
                """;

        Document chunk = RuleCorpusLoader.parse(crossCutting, "three-d-secure.md").get(0);

        assertThat(chunk.getText()).startsWith("ANY - 3-D Secure Authentication");
        assertThat(chunk.getText()).doesNotContain("reason code");
    }

    @Test
    void chunking_is_reproducible() {
        List<Document> first = RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md");
        List<Document> second = RuleCorpusLoader.parse(MINIMAL_SHEET, "visa-13-1.md");

        assertThat(second).extracting(Document::getId)
                .isEqualTo(first.stream().map(Document::getId).toList());
        assertThat(second).extracting(Document::getText)
                .isEqualTo(first.stream().map(Document::getText).toList());
    }

    @Test
    void the_real_classpath_corpus_loads_completely() throws Exception {
        Resource[] sheets = new PathMatchingResourcePatternResolver()
                .getResources("classpath:rules/*.md");

        List<Document> chunks = RuleCorpusLoader.load(sheets);

        assertThat(sheets).hasSize(15);
        assertThat(chunks).hasSize(90);
        assertThat(chunks).extracting(Document::getId).doesNotHaveDuplicates();
        assertThat(chunks).extracting(d -> d.getMetadata().get(RuleCorpusLoader.META_NETWORK))
                .contains("VISA", "MASTERCARD", RuleCorpusLoader.ANY);
    }

    @Test
    void a_sheet_without_front_matter_is_rejected() {
        assertThatThrownBy(() -> RuleCorpusLoader.parse("## Scope\n\nSome text.\n", "broken.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("front matter");
    }

    @Test
    void a_missing_front_matter_key_is_rejected() {
        String withoutReasonCode = """
                ---
                ruleId: visa-13.1
                network: VISA
                title: Merchandise or Services Not Received
                ---

                ## Scope

                Some text.
                """;

        assertThatThrownBy(() -> RuleCorpusLoader.parse(withoutReasonCode, "broken.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reasonCode");
    }

    @Test
    void an_empty_section_is_rejected() {
        String emptySection = """
                ---
                ruleId: visa-13.1
                network: VISA
                reasonCode: "13.1"
                title: Merchandise or Services Not Received
                ---

                ## Scope

                ## Time limits

                The issuer must raise the dispute within 120 calendar days.
                """;

        assertThatThrownBy(() -> RuleCorpusLoader.parse(emptySection, "broken.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Empty section");
    }

    @Test
    void a_sheet_without_any_section_is_rejected() {
        String withoutSection = """
                ---
                ruleId: visa-13.1
                network: VISA
                reasonCode: "13.1"
                title: Merchandise or Services Not Received
                ---

                A free paragraph, never introduced by a section heading.
                """;

        assertThatThrownBy(() -> RuleCorpusLoader.parse(withoutSection, "broken.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without any");
    }
}
