package com.dehnes.accounting.utils.kmymoney.booking_creators

import com.dehnes.accounting.database.*
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.kmymoney.AccountImporter
import com.dehnes.accounting.utils.kmymoney.AccountWrapper
import com.dehnes.accounting.utils.kmymoney.BookingCreator
import java.sql.Connection
import java.time.Instant

object InternalBookingCreator : BookingCreator {
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
    ): List<AddBooking> = listOf(AddBooking(realmId, mainSplit.memo, datetime, otherSplits.entries.map {
        AddBookingEntry(
            null, it.value.accountDto.id, it.key.amountInCents
        )
    }))
}