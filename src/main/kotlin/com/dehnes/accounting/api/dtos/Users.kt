package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.Realm
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import java.time.Instant
import java.time.LocalDate

data class User(
    override val id: String,
    override val name: String,
    override val description: String?,
    val userEmail: String,
    val active: Boolean,
    val admin: Boolean,
    val realmIdToAccessLevel: Map<String, RealmAccessLevel>,
) : InformationElement()


data class RealmInfo(
    override val id: String,
    override val name: String,
    override val description: String?,
    val closure: Instant,
) : InformationElement() {
    companion object {
        fun Realm.map() = RealmInfo(
            id = this.id,
            name = this.name,
            description = this.description,
            closure = LocalDate.of(
                this.closedYear,
                this.closedMonth,
                1
            ).atStartOfDay(zoneId).toInstant(),
        )
    }
}

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
