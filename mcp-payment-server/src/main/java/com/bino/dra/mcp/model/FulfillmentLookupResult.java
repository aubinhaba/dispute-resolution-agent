package com.bino.dra.mcp.model;

/**
 * Result envelope for {@code get_fulfillment_record}. A missing record is an answer, not an error:
 * {@code found=false} plus an explanatory {@code note} tells the model nothing was shipped (e.g. digital
 * goods), rather than leaving it to guess or retry.
 */
public record FulfillmentLookupResult(
        boolean found,
        FulfillmentRecordDto record,
        String note
) {

    public static FulfillmentLookupResult of(FulfillmentRecordDto record) {
        return new FulfillmentLookupResult(true, record,
                "Fulfillment record found for this transaction.");
    }

    public static FulfillmentLookupResult none() {
        return new FulfillmentLookupResult(false, null,
                "No fulfillment record exists for this transaction. Nothing was shipped "
                        + "(e.g. digital goods or a service): treat 'goods not received' claims accordingly.");
    }
}
