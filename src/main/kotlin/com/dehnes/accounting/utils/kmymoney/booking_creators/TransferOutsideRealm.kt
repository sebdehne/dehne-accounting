package com.dehnes.accounting.utils.kmymoney.booking_creators

import com.dehnes.accounting.database.*
import com.dehnes.accounting.domain.StandardAccount
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.kmymoney.AccountImporter
import com.dehnes.accounting.utils.kmymoney.AccountWrapper
import com.dehnes.accounting.utils.kmymoney.BookingCreator
import java.sql.Connection
import java.time.Instant

object TransferOutsideRealm : BookingCreator {
    override fun createBooking(
        realmId: String,
        transactionId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, AccountWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> {

        val intermediateAccount = if (mainSplit.amountInCents > 0) {
            accountImporter.getImportedAccountReceivable(connection, mainSplit.payeeId)
        } else {
            accountImporter.getImportedAccountPayable(connection, mainSplit.payeeId)
        }

        return listOf(
            AddBooking(
                realmId,
                mainSplit.memo,
                datetime,
                listOf(
                    AddBookingEntry(
                        null,
                        bankAccountDto.id,
                        mainSplit.amountInCents
                    ),
                    AddBookingEntry(
                        null,
                        intermediateAccount.accountDto.id,
                        mainSplit.amountInCents * -1
                    )
                )
            ),
            AddBooking(
                realmId,
                mainSplit.memo,
                datetime,
                listOf(
                    AddBookingEntry(
                        null,
                        intermediateAccount.accountDto.id,
                        mainSplit.amountInCents
                    ),
                    AddBookingEntry(
                        null,
                        StandardAccount.OtherBankTransfers.toAccountId(realmId),
                        mainSplit.amountInCents * -1
                    )
                )
            ),
        )
    }
}