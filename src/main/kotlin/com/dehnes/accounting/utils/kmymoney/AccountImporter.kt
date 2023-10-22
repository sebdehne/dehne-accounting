package com.dehnes.accounting.utils.kmymoney

import com.dehnes.accounting.database.AccountDto
import com.dehnes.accounting.database.AccountsRepository
import com.dehnes.accounting.database.Party
import com.dehnes.accounting.database.PartyRepository
import com.dehnes.accounting.domain.StandardAccount
import com.dehnes.accounting.kmymoney.AccountIdMapping
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.StringUtils.deterministicId
import java.sql.Connection
import java.util.*

class AccountImporter(
    private val accountIdMapping: AccountIdMapping,
    private val realmId: String,
    private val accountsRepository: AccountsRepository,
    private val partyRepository: PartyRepository,
    private val allPayees: List<KMyMoneyUtils.Payee>,
    private val payeeNameToAccountsReceivableAccount: Map<String, String>,
    private val defaultPayeeName: String,
) {

    private val alreadyImported = mutableMapOf<String, AccountWrapper>()
    private val accountsPayableAlreadyImported = mutableMapOf<String, AccountWrapper>()
    private val accountsReceivableAlreadyImported = mutableMapOf<String, AccountWrapper>()

    private val rootMappings = mapOf(
        "AStd::Asset" to StandardAccount.Asset,
        "AStd::Liability" to StandardAccount.Liability,
        "AStd::Equity" to StandardAccount.Equity,
        "AStd::Income" to StandardAccount.Income,
        "AStd::Expense" to StandardAccount.Expense,
    )

    fun getImported(connection: Connection, kAccountId: String) =
        alreadyImported.getOrPut(kAccountId) {
            val thisAccount = accountIdMapping.getById(kAccountId)
            val thisAccountPath = accountIdMapping.idToPath(thisAccount.id)

            val pathKAccounts = LinkedList<KMyMoneyUtils.Account>()
            var current: KMyMoneyUtils.Account? = thisAccount
            while (current != null) {
                pathKAccounts.addFirst(current)
                current = current.parentId?.let { kId -> accountIdMapping.getById(kId) }
            }

            if (payeeNameToAccountsReceivableAccount.any { it.value == thisAccountPath }) {
                val payeeName =
                    payeeNameToAccountsReceivableAccount.entries.first { it.value == thisAccountPath }.key
                val payee = allPayees.first { it.name == payeeName }
                return@getOrPut getImportedAccountReceivable(connection, payee.id)
            }

            val path = LinkedList<AccountDto>()
            pathKAccounts.forEach { kAccount ->
                if (kAccount.id in alreadyImported) {
                    alreadyImported[kAccount.id]!!.accountDto.apply {
                        path.add(this)
                    }
                } else {
                    if (kAccount.id in rootMappings) {
                        path.add(rootMappings[kAccount.id]!!.toAccountDto(realmId))
                    } else if (thisAccount.name == "Opening Balances"
                        && path.lastOrNull()?.id == StandardAccount.Equity.toAccountId(realmId)
                    ) {
                        path.add(StandardAccount.OpeningBalances.toAccountDto(realmId))
                    } else {
                        path.add(
                            AccountDto(
                                kAccount.id.deterministicId(realmId),
                                kAccount.name,
                                null,
                                realmId,
                                path.lastOrNull()?.id,
                                null,
                                false,
                            )
                        )
                        accountsRepository.insert(connection, path.last())
                    }

                    if (kAccount.id != thisAccount.id) {
                        alreadyImported[kAccount.id] = AccountWrapper(
                            path.toList().dropLast(1),
                            path.last()
                        )
                    }
                }
            }

            AccountWrapper(
                path.dropLast(1),
                path.last()
            )
        }

    fun getImportedAccountPayable(connection: Connection, payeeIdIn: String?): AccountWrapper {
        val payeeId = payeeIdIn ?: allPayees.first { it.name == defaultPayeeName }.id
        return accountsPayableAlreadyImported.getOrPut(payeeId) {

            val payee = allPayees.first { it.id == payeeId }
            val party = partyRepository.get(connection, payee.id.deterministicId(realmId)) ?: run {
                val party = Party(
                    payee.id.deterministicId(realmId), payee.name, null, realmId
                )
                partyRepository.insert(connection, party)
                party
            }

            val accountDto = AccountDto(
                party.id.deterministicId(realmId, "AccountPayable"),
                party.name,
                null,
                realmId,
                StandardAccount.AccountPayable.toAccountId(realmId),
                party.id,
                false,
            )

            accountsRepository.insert(connection, accountDto)

            val accountWrapper = AccountWrapper(
                listOf(
                    StandardAccount.AccountPayable.parent!!.toAccountDto(realmId),
                    StandardAccount.AccountPayable.toAccountDto(realmId),
                ), accountDto
            )
            accountWrapper
        }
    }

    fun getImportedAccountReceivable(connection: Connection, payeeIdIn: String?): AccountWrapper {
        val payeeId = payeeIdIn ?: allPayees.first { it.name == defaultPayeeName }.id
        return accountsReceivableAlreadyImported.getOrPut(payeeId) {

            val payee = allPayees.first { it.id == payeeId }
            val party = partyRepository.get(connection, payee.id.deterministicId(realmId)) ?: run {
                val party = Party(
                    payee.id.deterministicId(realmId), payee.name, null, realmId
                )
                partyRepository.insert(connection, party)
                party
            }

            val accountDto = AccountDto(
                party.id.deterministicId(realmId, "AccountReceivable"),
                party.name,
                null,
                realmId,
                StandardAccount.AccountReceivable.toAccountId(realmId),
                party.id,
                false,
            )

            accountsRepository.insert(connection, accountDto)

            val accountWrapper = AccountWrapper(
                listOf(
                    StandardAccount.AccountReceivable.parent!!.toAccountDto(realmId),
                    StandardAccount.AccountReceivable.toAccountDto(realmId),
                ), accountDto
            )
            accountWrapper
        }
    }
}

data class AccountWrapper(
    val parentPath: List<AccountDto>,
    val accountDto: AccountDto,
) {
    fun isBankAccount(realmId: String): Boolean =
        parentPath.first().id == StandardAccount.Asset.toAccountId(realmId)
                || parentPath.first().id == StandardAccount.Liability.toAccountId(realmId)
}
