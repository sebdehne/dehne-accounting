package com.dehnes.accounting.utils.kmymoney.booking_creators

import com.dehnes.accounting.database.*
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.DateTimeUtils.plusDays
import com.dehnes.accounting.utils.kmymoney.AccountImporter
import com.dehnes.accounting.utils.kmymoney.AccountWrapper
import com.dehnes.accounting.utils.kmymoney.BookingCreator
import java.sql.Connection
import java.time.Instant

object TransferInsideRealmCreator : BookingCreator {
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

        val bookings = bookingRepository.getBookings(
            realmId,
            Int.MAX_VALUE,
            listOf(
                DateRangeFilter(
                    datetime,
                    datetime.plusDays(1)
                )
            )
        )
        val alreadyBooked = bookings.filter { b ->
            if (b.entries.size != 2) return@filter false
            b.description?.contains(transactionId) == true
        }

        return if (alreadyBooked.isNotEmpty()) {
            check(alreadyBooked.size == 1)
            emptyList()
        } else {
            listOf(
                AddBooking(
                    realmId,
                    listOfNotNull(mainSplit.memo,transactionId).joinToString(","),
                    datetime,
                    listOf(
                        AddBookingEntry(
                            null,
                            bankAccountDto.id,
                            mainSplit.amountInCents
                        ),
                        AddBookingEntry(
                            null,
                            otherSplits.entries.single().value.accountDto.id,
                            mainSplit.amountInCents * -1
                        ),
                    )
                )
            )
        }
    }
}