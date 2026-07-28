package com.bino.dra.adapter.out.vectorstore;

import java.util.List;

// Identifiers only, never passage text: cited text is always re-read from the corpus (see ADR-0010)
public record RerankDraft(List<String> orderedIds) {
}
