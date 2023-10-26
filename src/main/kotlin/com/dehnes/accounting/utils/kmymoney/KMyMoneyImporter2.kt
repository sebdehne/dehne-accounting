package com.dehnes.accounting.utils.kmymoney

import com.dehnes.accounting.bank.importers.SupportedImporters
import com.dehnes.accounting.database.*
import com.dehnes.accounting.domain.StandardAccount
import com.dehnes.accounting.kmymoney.AccountIdMapping
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.getAllAccounts
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.getAllPayees
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.parseAllBanks
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.parseTransactions
import com.dehnes.accounting.kmymoney.utils.Gzip
import com.dehnes.accounting.kmymoney.utils.XmlUtils
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import com.dehnes.accounting.utils.StringUtils.deterministicId
import com.dehnes.accounting.utils.kmymoney.booking_creators.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.sql.Connection

enum class TransactionType(
    val bookingCreator: BookingCreator
) {
    openingBalance(OpeningBalanceCreator),
    internalBooking(InternalBookingCreator),
    paymentToParty(PaymentToParty),
    incomeFromParty(IncomeFromPartyCreator),
    transferOutsideRealm(TransferOutsideRealm),
    transferInsideRealm(TransferInsideRealmCreator),
}

class KMyMoneyImporter2(
    private val bankRepository: BankRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val accountsRepository: AccountsRepository,
    private val bookingRepository: BookingRepository,
    private val changelog: Changelog,
) {

    fun import(
        kMyMoneyFile: String,
        realmId: String,
        bankAccountNamesToImport: List<String>,
        banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
        payeeNameToAccountsReceivableAccount: Map<String, String>,
        defaultPayeeName: String,
    ) {
        val kMyMoneyRoot = XmlUtils.parseKMyMoneyFile(
            Gzip.uncompress(File(kMyMoneyFile).readBytes()).toString(StandardCharsets.UTF_8)
        )

        val accountIdMapping = AccountIdMapping.create(kMyMoneyRoot.getAllAccounts())

        val allBanks = kMyMoneyRoot.parseAllBanks()
        val allTransactions = kMyMoneyRoot.parseTransactions()

        val accountImporter = AccountImporter(
            accountIdMapping,
            realmId,
            accountsRepository,
            kMyMoneyRoot.getAllPayees(),
            payeeNameToAccountsReceivableAccount,
            defaultPayeeName,
        )

        val bankAccountImporter = BankAccountImporter(
            accountImporter,
            allBanks,
            accountIdMapping,
            bankRepository,
            bankAccountRepository,
            banknameToImporterFunctionMapper,
            realmId,
        )

        val bankAccountsToImport = changelog.writeTx { conn ->
            allBanks.flatMap { kBank ->
                kBank.accountIds.mapNotNull { kAccountId ->
                    val kAccount = accountIdMapping.getById(kAccountId)
                    if (kAccount.name in bankAccountNamesToImport) {
                        val (bankAccount, accountDto) = bankAccountImporter.getImportedBankAccount(
                            conn,
                            kAccount.id,
                        )
                        BankAccountWrapper(
                            kAccount.id,
                            bankAccount,
                            accountDto
                        )
                    } else null
                }
            }
        }

        bankAccountsToImport.forEach { (kAccountId, bankAccount, accountDto) ->
            changelog.writeTx { conn ->
                val transactions = allTransactions.filter { it.splits.any { it.accountId == kAccountId } }

                transactions.forEach { tx ->
                    importTransaction(
                        conn,
                        bankAccount,
                        accountDto,
                        accountImporter,
                        bookingRepository,
                        realmId,
                        bankAccountsToImport.map { it.kAccountId },
                        tx,
                    )
                }
            }
        }
    }

    data class BankAccountWrapper(
        val kAccountId: String,
        val bankAccount: BankAccount,
        val accountDto: AccountWrapper
    )

    private fun importTransaction(
        conn: Connection,
        bankAccount: BankAccount,
        bankAccountDto: AccountWrapper,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        realmId: String,
        bankAccountIdsInsideRealm: List<String>,
        transaction: KMyMoneyUtils.KTransaction,
    ) {

        val mainSplit = transaction.splits.single {
            it.accountId.deterministicId(realmId) == bankAccountDto.accountDto.id
        }
        val otherSplits = transaction.splits
            .filterNot { it.accountId == mainSplit.accountId }
            .associateWith { accountImporter.getImported(conn, it.accountId) }
        val datetime = transaction.postDate.atStartOfDay().atZone(zoneId).toInstant()

        val isAgainstOtherBankAccount = otherSplits.entries
            .singleOrNull()?.value?.isBankAccount(realmId) ?: false

        val type = when {
            isAgainstOtherBankAccount
                    && otherSplits.all { it.key.accountId in bankAccountIdsInsideRealm } ->
                TransactionType.transferInsideRealm

            isAgainstOtherBankAccount
                    && otherSplits.all { it.key.accountId !in bankAccountIdsInsideRealm } ->
                TransactionType.transferOutsideRealm

            mainSplit.amountInCents == 0L -> TransactionType.internalBooking

            mainSplit.amountInCents > 0 && otherSplits.all {
                it.value.accountDto.id == StandardAccount.OpeningBalances.toAccountId(realmId)
            } -> TransactionType.openingBalance

            mainSplit.amountInCents < 0 -> TransactionType.paymentToParty

            else -> TransactionType.incomeFromParty
        }

        type.bookingCreator.createBooking(
            realmId,
            transaction.id,
            datetime,
            mainSplit,
            otherSplits,
            bankAccount,
            bankAccountDto.accountDto,
            accountImporter,
            bookingRepository,
            conn
        ).forEach { b -> bookingRepository.insert(conn, b) }
    }
}

