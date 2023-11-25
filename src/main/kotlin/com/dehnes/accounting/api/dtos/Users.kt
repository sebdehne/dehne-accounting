package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.services.AccessLevel

data class User(
    override val id: String,
    override val name: String,
    override val description: String?,
    val userEmail: String,
    val active: Boolean,
    val admin: Boolean,
    val realmIdToAccessLevel: Map<String, AccessLevel>,
) : InformationElement()

