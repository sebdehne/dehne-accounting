package com.dehnes.accounting.api.dtos

data class UserState(
    val userId: String,
    val frontendState: Map<String, Any>,
)

