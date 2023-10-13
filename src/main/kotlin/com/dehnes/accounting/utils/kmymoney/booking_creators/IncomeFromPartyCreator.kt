package com.dehnes.accounting.utils.kmymoney.booking_creators

import com.dehnes.accounting.database.*
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.kmymoney.AccountImporter
import com.dehnes.accounting.utils.kmymoney.AccountWrapper
import com.dehnes.accounting.utils.kmymoney.BookingCreator
import java.sql.Connection
import java.time.Instant

object IncomeFromPartyCreator : BookingCreator {
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

        val accountReceivable = accountImporter.getImportedAccountReceivable(
            connection,
            mainSplit.payeeId
        ).accountDto

        return listOf(
            AddBooking(
                realmId, mainSplit.memo, datetime, listOf(
                    AddBookingEntry(
                        null, bankAccountDto.id, mainSplit.amountInCents
                    ), AddBookingEntry(null, accountReceivable.id, otherSplits.entries.sumOf { it.key.amountInCents })
                )
            ),
            AddBooking(realmId, mainSplit.memo, datetime, listOf(
                AddBookingEntry(
                    null, accountReceivable.id, mainSplit.amountInCents
                )
            ) + otherSplits.entries.map {
                AddBookingEntry(
                    null, it.value.accountDto.id, it.key.amountInCents
                )
            })
        )
    }
}