package com.dehnes.accounting.services

import com.dehnes.accounting.api.UnbookedTransactionMatchersChanged
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import java.time.Instant
import javax.sql.DataSource

class UnbookedBankTransactionMatcherService(
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val authorizationService: AuthorizationService,
    private val unbookedBankTransactionMatcherRepository: UnbookedBankTransactionMatcherRepository,
    private val dataSource: DataSource,
    private val changelog: Changelog,
) {



    fun removeMatcher(
        userId: String,
        realmId: String,
        matcherId: String
    ) {
        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.write)

            unbookedBankTransactionMatcherRepository.remove(
                conn,
                matcherId,
                realmId
            )

            changelog.addV2(UnbookedTransactionMatchersChanged)
        }
    }


    fun addOrReplaceMatcher(
        userId: String,
        realmId: String,
        matcher: UnbookedBankTransactionMatcher
    ) {
        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.write)

            check(matcher.realmId == realmId)

            val updated = unbookedBankTransactionMatcherRepository.update(
                conn,
                matcher.copy(lastUsed = Instant.now())
            )
            if (!updated) {
                unbookedBankTransactionMatcherRepository.insert(
                    conn,
                    matcher.copy(lastUsed = Instant.now())
                )
            }


            changelog.addV2(UnbookedTransactionMatchersChanged)
        }
    }

    data class UnbookedBankTransactionReference(
        val accountId: String,
        val unbookedTransactionId: Long
    )

    fun getMatchers(
        userId: String,
        realmId: String,
        unbookedBankTransactionReference: UnbookedBankTransactionReference?,
    ): List<MatchedUnbookedBankTransactionMatcher> = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)

        val unbookedTransaction = unbookedBankTransactionReference?.let {
            unbookedTransactionRepository.getUnbookedTransaction(
                conn,
                realmId,
                it.accountId,
                it.unbookedTransactionId
            )
        }


        unbookedBankTransactionMatcherRepository.getAll(conn, realmId).map { unbookedBankTransactionMatcher ->
            MatchedUnbookedBankTransactionMatcher(
                unbookedTransaction?.let { unbookedBankTransactionMatcher.filter.matches(it) } ?: false,
                unbookedBankTransactionMatcher
            )
        }
    }

    fun getUnbookedBankTransaction(
        userId: String,
        realmId: String,
        unbookedBankTransactionReference: UnbookedBankTransactionReference
    ) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)
        unbookedTransactionRepository.getUnbookedTransaction(
            conn,
            realmId,
            unbookedBankTransactionReference.accountId,
            unbookedBankTransactionReference.unbookedTransactionId
        )
    }
}

data class MatchedUnbookedBankTransactionMatcher(
    val matches: Boolean,
    val matcher: UnbookedBankTransactionMatcher,
)

