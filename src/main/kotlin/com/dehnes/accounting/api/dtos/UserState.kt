package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.DateRangeFilter

data class UserState(
    val frontendState: Map<String, Any>,
)

data class UserStateV2(
    val id: String,
    val selectedRealm: String? = null,
    val rangeFilter: DateRangeFilter? = null,
    val periodType: String? = null
)

