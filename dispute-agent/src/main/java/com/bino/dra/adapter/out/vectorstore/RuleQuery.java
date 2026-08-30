package com.bino.dra.adapter.out.vectorstore;

import org.springframework.ai.rag.Query;

import java.util.Map;

public final class RuleQuery {

    public static final String CTX_REASON_CODE = "disputeReasonCode";
    public static final String CTX_NETWORK = "disputeNetwork";

    private RuleQuery() {
    }

    public static Query of(String reasonCode, String network) {
        return Query.builder()
                .text("%s reason code %s chargeback".formatted(network, reasonCode))
                .context(Map.of(CTX_REASON_CODE, reasonCode, CTX_NETWORK, network))
                .build();
    }

    static String reasonCodeOf(Query query) {
        return read(query, CTX_REASON_CODE);
    }

    static String networkOf(Query query) {
        return read(query, CTX_NETWORK);
    }

    private static String read(Query query, String key) {
        if (query == null || query.context() == null) {
            return "";
        }
        Object value = query.context().get(key);
        return value instanceof String text ? text : "";
    }
}
