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
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, AccountWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> {

        val alreadyBooked = bookingRepository.getBookings(
            connection, realmId, Int.MAX_VALUE, DateRangeFilter(
                datetime, datetime.plusDays(1)
            )
        ).any { b ->
            if (b.entries.size != 2) return@any false

            val mainEntry = b.entries.firstOrNull { it.accountId == bankAccountDto.id } ?: return@any false
            val otherEntry = b.entries.firstOrNull { it.accountId != bankAccountDto.id } ?: run {
                TODO()
            }

            otherSplits.entries.single().value.accountDto.id == otherEntry.accountId && mainEntry.amountInCents == mainSplit.amountInCents
        }

        return if (alreadyBooked) {
            emptyList()
        } else {
            listOf(
                AddBooking(
                    realmId, mainSplit.memo, datetime, listOf(
                        AddBookingEntry(
                            null, bankAccountDto.id, mainSplit.amountInCents
                        ),
                        AddBookingEntry(
                            null, otherSplits.entries.single().value.accountDto.id, mainSplit.amountInCents * -1
                        ),
                    )
                )
            )
        }
    }
}