package com.dehnes.accounting.utils.kmymoney

import com.dehnes.accounting.bank.importers.SupportedImporters
import com.dehnes.accounting.database.BankAccount
import com.dehnes.accounting.database.BankAccountRepository
import com.dehnes.accounting.database.BankDto
import com.dehnes.accounting.database.BankRepository
import com.dehnes.accounting.kmymoney.AccountIdMapping
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.DateTimeUtils
import com.dehnes.accounting.utils.StringUtils.deterministicId
import java.sql.Connection
import java.time.LocalDate

class BankAccountImporter(
    private val accountImporter: AccountImporter,
    private val allBanks: List<KMyMoneyUtils.KBank>,
    private val accountIdMapping: AccountIdMapping,
    private val bankRepository: BankRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
    private val realmId: String,
) {
    private val alreadyImported = mutableMapOf<String, Pair<BankAccount, AccountWrapper>>()

    fun getImportedBankAccount(
        connection: Connection,
        kAccountId: String,
    ) = alreadyImported.getOrPut(kAccountId) {

        val account = accountIdMapping.getById(kAccountId)
        val kBank = allBanks.first { it.accountIds.any { it == kAccountId } }
        val bank = bankRepository.getByName(connection, kBank.name) ?: run {
            val bank = BankDto(
                kBank.id.deterministicId(realmId),
                kBank.name,
                null,
                banknameToImporterFunctionMapper(kBank.name)?.name
            )
            bankRepository.insert(connection, bank)
            bank
        }

        val accountDtoWrapper = accountImporter.getImported(connection, account.id)

        val bankAccount = bankAccountRepository.insertBankAccount(
            connection,
            accountDtoWrapper.accountDto,
            bank.id,
            null,
            (account.opened ?: LocalDate.now()).atStartOfDay().atZone(DateTimeUtils.zoneId).toInstant(),
            null
        )

        bankAccount to accountDtoWrapper
    }

}