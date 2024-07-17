package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.InformationElement
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

class BankAccountService(
    private val bookingRepository: BookingRepository,
    private val bankRepository: BankRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val changelog: Changelog,
) {

    fun getBankAccount(userId: String, realmId: String, accountId: String) =
        dataSource.readTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.admin,
            )

            val bankAccountDto =
                bankAccountRepository.getAllBankAccounts(conn, realmId).first { it.accountId == accountId }
            BankAccount(
                bankAccountDto.accountId,
                bankAccountDto.bankId,
                bankAccountDto.accountNumber,
                bankAccountDto.openDate,
                bankAccountDto.closeDate,
            )
        }

    fun deleteBankAccount(userId: String, realmId: String, accountId: String) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.admin,
            )
            bankAccountRepository.deleteBankAccount(conn, accountId)
        }
    }

    fun createOrUpdateBankAccount(userId: String, realmId: String, bankAccount: BankAccount) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.admin,
            )

            val existing = bankAccountRepository.getAllBankAccounts(conn, realmId)
                .firstOrNull { it.accountId == bankAccount.accountId }
            if (existing != null) {
                bankAccountRepository.updateBankAccount(conn, bankAccount)
            } else {
                bankAccountRepository.insertBankAccount(conn, bankAccount)
            }
        }
    }


    fun deleteAllUnbookedTransactions(userId: String, realmId: String, accountId: String) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write,
            )

            unbookedTransactionRepository.deleteAll(conn, accountId)
        }
    }

    fun getOverview(userId: String, realmId: String) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )
        getOverview(conn, realmId)
    }

    private fun getOverview(conn: Connection, realmId: String): List<BankWithAccounts> {
        val allBanks = bankRepository.getAll(conn)

        val allBankAccounts = bankAccountRepository.getAllBankAccounts(conn, realmId)

        return allBanks.map { bank ->

            val accounts = allBankAccounts.filter { it.bankId == bank.id }

            BankWithAccounts(
                bank.id,
                bank.name,
                bank.description,
                accounts.map { ba ->
                    val lastKnownUnbookedTransactionDate =
                        unbookedTransactionRepository.getLastKnownUnbookedTransactionDate(
                            conn,
                            ba.accountId
                        )

                    val lastKnownBookingDate = bookingRepository.getLastKnownBookingDate(
                        ba.accountId,
                        realmId
                    )


                    BankAccountView(
                        ba.accountId,
                        ba.accountNumber,
                        ba.openDate,
                        ba.closeDate,
                        bookingRepository.getSum(
                            realmId = realmId,
                            accountId = ba.accountId,
                        ).first + unbookedTransactionRepository.getSum(
                            conn,
                            ba.accountId,
                            BankTxDateRangeFilter()
                        ),
                        listOfNotNull(
                            lastKnownBookingDate,
                            lastKnownUnbookedTransactionDate
                        ).maxOrNull()
                    )
                },
            )
        }
    }

    fun deleteUnbookedTransaction(
        userId: String,
        realmId: String,
        accountId: String,
        deleteUnbookedBankTransactionId: Long
    ) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write,
            )

            unbookedTransactionRepository.delete(
                conn,
                accountId,
                deleteUnbookedBankTransactionId
            )
        }
    }


}

data class BankWithAccounts(
    override val id: String,
    override val name: String,
    override val description: String?,
    val accounts: List<BankAccountView>,
) : InformationElement()

data class BankAccountView(
    val accountId: String,
    val accountNumber: String?,
    val openDate: Instant,
    val closeDate: Instant?,
    val balance: Long,
    val lastKnownTransactionDate: Instant?
)



