package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.domain.InformationElement

data class User(
    override val id: String,
    override val name: String,
    override val description: String?,
    val userEmail: String,
    val active: Boolean,
    val admin: Boolean,
    val realmIdToAccessLevel: Map<String, RealmAccessLevel>,
) : InformationElement()

data class UserInfo(
    val isAdmin: Boolean,
    val accessibleRealms: List<RealmInfoWithAccessLevel>,
)

data class RealmInfo(
    override val id: String,
    override val name: String,
    override val description: String?,
) : InformationElement()

data class RealmInfoWithAccessLevel(
    override val id: String,
    override val name: String,
    override val description: String?,
    val accessLevel: RealmAccessLevel,
) : InformationElement()

enum class RealmAccessLevel {
    read,
    readWrite,
    owner
}
