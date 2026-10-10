package com.bino.dra.adapter.out.llm;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

// Field order is generation order: objections first, so the boolean concludes reasoning already written
@JsonPropertyOrder({"objections", "coherent"})
public record ReviewDraft(List<String> objections, boolean coherent) {
}
