package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccessRequest
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
    ) = listRealms(connection, userId, accessRequest)

    fun assertAuthorization(
        connection: Connection,
        userId: String,
        realmId: String,
        accessRequest: AccessRequest,
    ) {
        listRealms(connection, userId, accessRequest).firstOrNull { it.id == realmId }
            ?: error("User $userId has not access to $realmId")
    }

    private fun listRealms(connection: Connection, userId: String, accessRequest: AccessRequest): List<Realm> {
        val user = userRepository.getUser(connection, userId) ?: error("Unknown userId=$userId")
        if (!user.active) return emptyList()

        val allRealms = realmRepository.getAll(connection)
        val userRealms = userRepository.getUserRealms(connection).filter { it.userId == user.id }

        return allRealms
            .map { realm ->
                val userRealm = userRealms.firstOrNull { it.ledgerId == realm.id }
                realm to userRealm
            }
            .filter { user.admin || it.second?.accessLevel?.hasAccess(accessRequest) == true }
            .map { it.first }
    }
}