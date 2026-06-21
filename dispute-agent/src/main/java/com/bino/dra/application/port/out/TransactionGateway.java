package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.FulfillmentRecord;
import com.bino.dra.domain.model.Transaction;

import java.util.List;
import java.util.Optional;

/**
 * Driven port for transactional data, backed by read-only MCP tools. Covers data that changes;
 * business rules go through {@link RuleRetriever} (RAG) instead (see ADR-0001). Lives in
 * {@code application} (see ADR-0007); no method mutates payment data.
 */
public interface TransactionGateway {

    /** The disputed transaction and its risk signals. MCP tool: {@code get_transaction}. */
    Transaction getTransaction(String transactionId);

    /** Customer behavioural history (returns summaries, not dumps). MCP tool: {@code get_customer_history}. */
    List<Transaction> getCustomerHistory(String customerRef, int lookbackDays, int limit);

    /** Related transactions (refunds, prior authorizations). MCP tool: {@code get_related_transactions}. */
    List<Transaction> getRelatedTransactions(String transactionId);

    /** Delivery proof ("goods not received" path); empty when there is no logistics leg. */
    Optional<FulfillmentRecord> getFulfillmentRecord(String transactionId);
}
