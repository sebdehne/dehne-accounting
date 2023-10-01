package com.dehnes.accounting.api.dtos

import java.time.Instant

data class LedgerRapportRequest(
    val from: Instant,
    val toExcluding: Instant,
)

data class LedgerRapportNode(
    val accountName: String,
    val totalAmountInCents: Long,
    val bookingRecords: List<LedgerRapportBookingRecord>,
    val children: List<LedgerRapportNode>,
)

data class LedgerRapportBookingRecord(
    val bookingId: Long,
    val id: Long,
    val datetime: Instant,
    val amountInCents: Long,
    val description: String?,
    val contraRecords: List<LedgerRapportBookingContraRecord>,
)

data class LedgerRapportBookingContraRecord(
    val accountName: String,
    val bookingId: Long,
    val id: Long,
)


