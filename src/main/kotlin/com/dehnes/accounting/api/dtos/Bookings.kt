package com.dehnes.accounting.api.dtos

import java.time.Instant

data class GetBookingsRequest(
    val from: Instant,
    val toExcluding: Instant,
    val limit: Int?,
)