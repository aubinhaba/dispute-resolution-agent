package com.bino.dra.mcp.tools;

import com.bino.dra.mcp.data.MockPaymentDataStore;
import com.bino.dra.mcp.model.FulfillmentLookupResult;
import com.bino.dra.mcp.model.TransactionDto;
import com.bino.dra.mcp.model.TransactionSummaryDto;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The four read-only transactional tools of the MCP server, over mocked data. The {@code @McpTool}
 * descriptions are the model-facing contract (sent at handshake), so they are deliberately verbose.
 * Inputs are validated: an unknown or blank id raises {@link IllegalArgumentException}, which the MCP
 * layer returns as an error result without killing the server.
 */
@Component
public class PaymentTools {

    private static final int DEFAULT_LOOKBACK_DAYS = 90;
    private static final int MAX_LOOKBACK_DAYS = 365;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final MockPaymentDataStore store;

    public PaymentTools(MockPaymentDataStore store) {
        this.store = store;
    }

    @McpTool(
            name = "get_transaction",
            description = """
                    Retrieve the full payment transaction for a dispute, including its risk signals. \
                    Start every dispute investigation with this tool. \
                    Returns: amount (minorUnits + human-readable 'formatted'), capture timestamp (ISO-8601), \
                    PSP, card brand and last-4 digits, and the risk signals: scaResult (3-D Secure: \
                    AUTHENTICATED means liability shifted to the issuer; ATTEMPTED, NOT_AUTHENTICATED, \
                    NOT_APPLIED), avsCheck and cvvCheck (MATCH, PARTIAL_MATCH, MISMATCH, NOT_CHECKED), \
                    and ipCountry vs billingCountry (a mismatch is a fraud signal). \
                    customerRef is a token: use it as-is with get_customer_history.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public TransactionDto getTransaction(
            @McpToolParam(description = "Transaction identifier exactly as given in the dispute, e.g. 'TXN-EVAL-003'.",
                    required = true)
            String transactionId) {
        requireText(transactionId, "transactionId");
        return store.findTransaction(transactionId)
                .orElseThrow(() -> unknownTransaction(transactionId));
    }

    @McpTool(
            name = "get_customer_history",
            description = """
                    Summarized payment history of a customer, most recent first. \
                    Use it to judge behavioural context: is this a long-standing customer with a regular \
                    purchase pattern (supports fighting a fraud claim), or a recent one with no track \
                    record (supports accepting it)? \
                    Each entry is a summary (transactionId, merchant, amount, capture date, scaResult); \
                    call get_transaction on a specific id if you need its full risk signals. \
                    An empty list means the customer has no transactions in the window - that is itself \
                    a signal (new or dormant customer), not an error.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<TransactionSummaryDto> getCustomerHistory(
            @McpToolParam(description = "Tokenized customer reference from get_transaction, e.g. 'CUST-M4XA1'.",
                    required = true)
            String customerRef,
            @McpToolParam(description = "How many days back to search. Optional, default 90, max 365.",
                    required = false)
            Integer lookbackDays,
            @McpToolParam(description = "Maximum number of entries returned. Optional, default 20, max 50.",
                    required = false)
            Integer limit) {
        requireText(customerRef, "customerRef");
        if (!store.knowsCustomer(customerRef)) {
            // Unknown customer ≠ customer with no recent history: keep them distinct.
            throw new IllegalArgumentException("Unknown customerRef '" + customerRef
                    + "'. Use the exact customerRef returned by get_transaction.");
        }
        // Clamp rather than reject: serves the intent while protecting the context budget.
        int days = clamp(lookbackDays, DEFAULT_LOOKBACK_DAYS, MAX_LOOKBACK_DAYS);
        int max = clamp(limit, DEFAULT_LIMIT, MAX_LIMIT);
        return store.customerHistory(customerRef, days, max);
    }

    @McpTool(
            name = "get_related_transactions",
            description = """
                    Transactions related to the disputed one: earlier purchases with the same card at \
                    the same merchant, prior authorizations, refunds already issued. \
                    Use it to check (a) whether the card has an established usage pattern with this \
                    merchant (supports representment of a fraud claim) and (b) whether the customer was \
                    already refunded (a dispute on an already-refunded transaction is usually accepted \
                    or flagged). Returns full transaction objects. \
                    An empty list is a valid answer meaning no related activity - not an error.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<TransactionDto> getRelatedTransactions(
            @McpToolParam(description = "Identifier of the disputed transaction, e.g. 'TXN-EVAL-002'.",
                    required = true)
            String transactionId) {
        requireText(transactionId, "transactionId");
        // Optional.empty() = unknown id (error); empty list = "nothing related" (valid answer).
        return store.relatedTransactions(transactionId)
                .orElseThrow(() -> unknownTransaction(transactionId));
    }

    @McpTool(
            name = "get_fulfillment_record",
            description = """
                    Shipping and delivery evidence for a transaction. \
                    Essential for 'goods not received' disputes (e.g. Visa reason code 13.1): a record \
                    with shipped=true and deliveryStatus=DELIVERED is strong representment evidence. \
                    The response always contains 'found': when found=false, no fulfillment record exists \
                    (digital goods or services - nothing was ever shipped); read the 'note' field and \
                    weigh the claim accordingly instead of retrying.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public FulfillmentLookupResult getFulfillmentRecord(
            @McpToolParam(description = "Identifier of the disputed transaction, e.g. 'TXN-EVAL-003'.",
                    required = true)
            String transactionId) {
        requireText(transactionId, "transactionId");
        if (!store.knowsTransaction(transactionId)) {
            throw unknownTransaction(transactionId); // unknown id = error...
        }
        return store.findFulfillment(transactionId)
                .map(FulfillmentLookupResult::of)
                .orElseGet(FulfillmentLookupResult::none); // ...no record = a valid answer.
    }

    private static void requireText(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Parameter '" + paramName + "' is required and must not be blank.");
        }
    }

    /** Actionable message for the model — what to fix, no stack trace. */
    private static IllegalArgumentException unknownTransaction(String transactionId) {
        return new IllegalArgumentException("Unknown transactionId '" + transactionId
                + "'. Double-check the identifier provided in the dispute; do not guess or fabricate ids.");
    }

    private static int clamp(Integer requested, int defaultValue, int max) {
        if (requested == null || requested <= 0) {
            return defaultValue;
        }
        return Math.min(requested, max);
    }
}
