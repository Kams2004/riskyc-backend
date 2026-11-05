package com.fashion.Riskyc.dto.response;

import java.util.UUID;

/**
 * Outcome for one queued sale. RECORDED and DUPLICATE both mean "the server has it — drop it from the queue";
 * REJECTED means it can never succeed as sent (keep it aside and show the cashier the error); ERROR means a
 * temporary server problem — keep it queued and send it again.
 */
public record DeviceSaleResultResponse(UUID clientSaleId, String status, UUID saleId, String error) {
}
