package com.dehnes.accounting.utils.kmymoney.booking_creators

import com.dehnes.accounting.database.*
import com.dehnes.accounting.domain.StandardAccount
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.kmymoney.AccountImporter
import com.dehnes.accounting.utils.kmymoney.AccountWrapper
import com.dehnes.accounting.utils.kmymoney.BookingCreator
import java.sql.Connection
import java.time.Instant

object OpeningBalanceCreator : BookingCreator {
    override fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, AccountWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> = listOf(
        AddBooking(
            realmId, null, datetime, listOf(
                AddBookingEntry(
                    null,
                    bankAccountDto.id,
                    mainSplit.amountInCents,
                ), AddBookingEntry(
                    null, StandardAccount.OpeningBalances.toAccountId(realmId), mainSplit.amountInCents * -1
                )
            )
        )
    )
}