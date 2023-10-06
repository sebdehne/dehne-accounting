package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.AccessLevel
import com.dehnes.accounting.domain.InformationElement

data class LedgerView(
    override val id: String,
    override val name: String,
    override val description: String?,
    val accessLevel: AccessLevel,
    val currency: String,
) : InformationElement()

