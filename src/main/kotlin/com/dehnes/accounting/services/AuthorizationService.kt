package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.RealmAccessLevel
import com.dehnes.accounting.database.Realm
import com.dehnes.accounting.database.RealmRepository
import com.dehnes.accounting.database.UserRepository
import com.dehnes.accounting.utils.DateTimeUtils
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate

class AuthorizationService(
    private val userRepository: UserRepository,
    private val realmRepository: RealmRepository,
) {


    fun assertAuthorization(
        connection: Connection,
        userId: String,
        realmId: String,
        accessRequest: AccessRequest,
        vararg dates: Instant?
    ) {
        val realm = (listAuthorizedRealms(connection, userId, accessRequest)
            .firstOrNull { it.id == realmId }
            ?: error("User $userId has not access to $realmId"))

        if (accessRequest == AccessRequest.read) return

        dates.toList().filterNotNull().forEach { timestamp ->
            val closed = LocalDate.of(
                realm.closedYear,
                realm.closedMonth,
                1,
            ).plusMonths(1)
                .atStartOfDay(DateTimeUtils.zoneId)
                .toInstant()

            if (closed.isAfter(timestamp)) {
                error("Cannot write for date=$timestamp because period is closed =${realm.closedYear}-${realm.closedMonth}")
            }
        }
    }

    fun assertIsAdmin(
        connection: Connection,
        userId: String,
    ) {
        check(userRepository.getUser(connection, userId).admin) { "User $userId is not an admin" }
    }

    private fun listAuthorizedRealms(
        connection: Connection,
        userId: String,
        accessRequest: AccessRequest
    ): List<Realm> {
        val user = userRepository.getUser(connection, userId)
        if (!user.active) return emptyList()

        val realmList = realmRepository.getAll(connection)

        val permissions = if (user.admin) {
            realmList.associate {
                it.id to RealmAccessLevel.owner
            }
        } else {
            user.realmIdToAccessLevel
        }

        return permissions.entries
            .filter { user.admin || it.value.hasAccess(accessRequest) }
            .map { entry ->
                realmList.first { it.id == entry.key }
            }
    }
}

enum class AccessRequest {
    read,
    write,
    owner,
    admin
}

fun RealmAccessLevel.hasAccess(req: AccessRequest) = when (req) {
    AccessRequest.admin,
    AccessRequest.owner -> this == RealmAccessLevel.owner

    AccessRequest.write -> this in listOf(RealmAccessLevel.owner, RealmAccessLevel.readWrite)
    AccessRequest.read -> this in listOf(RealmAccessLevel.owner, RealmAccessLevel.readWrite, RealmAccessLevel.read)
}
