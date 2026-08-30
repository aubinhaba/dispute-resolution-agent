package com.bino.dra.adapter.out.vectorstore;

import com.bino.dra.adapter.out.support.Text;
import org.springframework.ai.document.Document;

public final class RuleChunks {

    static final String BODY_SEPARATOR = "\n\n";

    private RuleChunks() {
    }

    // Not getId(), which holds the UUID PgVectorStore requires: anything shown to a model or
    // matched against its answer must use this id, or the two namespaces stop overlapping
    public static String chunkId(Document chunk) {
        String auditId = metadata(chunk, RuleCorpusLoader.META_CHUNK_ID);
        return auditId.isBlank() ? chunk.getId() : auditId;
    }

    public static String ruleId(Document chunk) {
        return metadata(chunk, RuleCorpusLoader.META_RULE_ID);
    }

    public static String title(Document chunk) {
        return metadata(chunk, RuleCorpusLoader.META_TITLE);
    }

    public static String section(Document chunk) {
        return metadata(chunk, RuleCorpusLoader.META_SECTION);
    }

    public static String reasonCode(Document chunk) {
        return metadata(chunk, RuleCorpusLoader.META_REASON_CODE);
    }

    public static String appliesTo(Document chunk) {
        return metadata(chunk, RuleCorpusLoader.META_APPLIES_TO);
    }

    public static String body(Document chunk) {
        String text = chunk.getText() == null ? "" : chunk.getText();
        String[] parts = text.split(BODY_SEPARATOR, 2);
        return Text.flatten(parts.length == 2 ? parts[1] : text);
    }

    public static String metadata(Document chunk, String key) {
        Object value = chunk.getMetadata().get(key);
        return value instanceof String text ? text : "";
    }
}
