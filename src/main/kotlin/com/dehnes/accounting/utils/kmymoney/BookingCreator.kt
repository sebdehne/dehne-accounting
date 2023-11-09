package com.dehnes.accounting.utils.kmymoney

import com.dehnes.accounting.database.*
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import java.sql.Connection
import java.time.Instant

interface BookingCreator {
    fun createBooking(
        realmId: String,
        transactionId: String,
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