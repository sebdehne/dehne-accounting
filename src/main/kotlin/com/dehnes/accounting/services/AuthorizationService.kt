package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.RealmAccessLevel
import com.dehnes.accounting.api.dtos.RealmInfo
import com.dehnes.accounting.database.Realm
import com.dehnes.accounting.database.RealmRepository
import com.dehnes.accounting.database.UserRepository
import java.sql.Connection

class AuthorizationService(
    private val userRepository: UserRepository,
    private val realmRepository: RealmRepository,
) {


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

    fun getRealmInfo(connection: Connection, userId: String): List<RealmInfo> {
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
            .filter { user.admin || it.value.hasAccess(AccessRequest.read) }
            .map { e ->
                val realm = realmList.first { it.id == e.key }
                RealmInfo(
                    realm.id,
                    realm.name,
                    realm.description,
                    e.value
                )
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
    ;


}

fun RealmAccessLevel.hasAccess(req: AccessRequest) = when (req) {
    AccessRequest.admin,
    AccessRequest.owner -> this == RealmAccessLevel.owner

    AccessRequest.write -> this in listOf(RealmAccessLevel.owner, RealmAccessLevel.readWrite)
    AccessRequest.read -> this in listOf(RealmAccessLevel.owner, RealmAccessLevel.readWrite, RealmAccessLevel.read)
}
