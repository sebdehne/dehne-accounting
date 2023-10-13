package com.dehnes.accounting.utils.kmymoney

import com.dehnes.accounting.database.AccountDto
import com.dehnes.accounting.database.AddBooking
import com.dehnes.accounting.database.BankAccount
import com.dehnes.accounting.database.BookingRepository
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import java.sql.Connection
import java.time.Instant

interface BookingCreator {
    fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, AccountWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection,
    ): List<AddBooking>
}