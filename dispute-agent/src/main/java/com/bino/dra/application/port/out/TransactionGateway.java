package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.FulfillmentRecord;
import com.bino.dra.domain.model.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionGateway {

    Transaction getTransaction(String transactionId);

    List<Transaction> getCustomerHistory(String customerRef, int lookbackDays, int limit);

    List<Transaction> getRelatedTransactions(String transactionId);

    Optional<FulfillmentRecord> getFulfillmentRecord(String transactionId);
}
