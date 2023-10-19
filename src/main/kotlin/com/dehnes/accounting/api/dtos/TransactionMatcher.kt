package com.dehnes.accounting.api.dtos


data class ExecuteMatcherRequest(
    val accountId: String,
    val transactionId: Long,
    val matcherId: String,
    val overrideMemo: String?,
)
