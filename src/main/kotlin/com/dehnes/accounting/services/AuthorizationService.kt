package com.dehnes.accounting.services

import com.dehnes.accounting.database.Realm
import com.dehnes.accounting.database.RealmRepository
import com.dehnes.accounting.database.UserRepository
import java.sql.Connection

class AuthorizationService(
    private val userRepository: UserRepository,
    private val realmRepository: RealmRepository,
) {

    fun getAuthorizedRealms(
        connection: Connection,
        userId: String,
        accessRequest: AccessRequest,
    ) = listAuthorizedRealms(connection, userId, accessRequest)

    fun assertAuthorization(
        connection: Connection,
        userId: String,
        realmId: String,
        accessRequest: AccessRequest,
    ) {
        listAuthorizedRealms(connection, userId, accessRequest).firstOrNull { it.id == realmId }
            ?: error("User $userId has not access to $realmId")
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
                it.id to AccessLevel.admin
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

enum class AccessLevel {
    admin,
    realmOwner,
    realmReadWrite,
    realmRead,
    none,
    ;

    fun hasAccess(req: AccessRequest) = when (req) {
        AccessRequest.admin -> this == admin
        AccessRequest.owner -> this in listOf(admin, realmOwner)
        AccessRequest.write -> this in listOf(admin, realmOwner, realmReadWrite)
        AccessRequest.read -> this in listOf(admin, realmOwner, realmReadWrite, realmRead)
    }
}