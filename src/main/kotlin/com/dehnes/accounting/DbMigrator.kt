package com.dehnes.accounting

import com.dehnes.accounting.database.*
import com.dehnes.accounting.domain.StandardAccount
import java.util.concurrent.Executors

object DbMigrator {

    @JvmStatic
    fun main(args: Array<String>) {
        val datasource = datasourceSetup(dbFile("accounting_data"))

        val executorService = Executors.newCachedThreadPool()
        val objectMapper = objectMapper()

        val changelog = Changelog(datasource, executorService)
        val accountsRepository = AccountsRepository(changelog)
        val realmRepository = RealmRepository(accountsRepository, changelog)
        val bookingRepository = BookingRepository(
            realmRepository,
            changelog,
            datasource
        )

        val realmList = changelog.writeTx {
            realmRepository.getAll(it)
        }

        realmList.forEach { realm ->

            val allAccounts = datasource.connection.use {
                accountsRepository.getAll(it, realm.id)
            }
            val accountPayable = allAccounts.standardAccounts.single { it.standardAccount == StandardAccount.AccountPayable }
            val accountReceivable = allAccounts.standardAccounts.single { it.standardAccount == StandardAccount.AccountReceivable }
            val accountIncome = allAccounts.standardAccounts.single { it.standardAccount == StandardAccount.Income }
            val accountExpense = allAccounts.standardAccounts.single { it.standardAccount == StandardAccount.Expense }

            fun getPath(aId: String, path: List<AccountDto> = emptyList()): List<AccountDto> {
                val a = allAccounts.allAccounts.single { it.id == aId }
                if (a.parentAccountId == null) {
                    return path
                }
                return getPath(a.parentAccountId, listOf(a) + path)
            }

            val candidates = mutableListOf<List<Booking>>()

            bookingRepository.getBookings(
                realm.id,
                Int.MAX_VALUE,
                emptyList()
            ).groupBy { it.datetime }.entries.map {
                it.key to it.value
            }.sortedBy { it.first }.forEach { (date, bookings) ->

                bookings.mapNotNull { b ->
                    val entries = b.entries.filter { e ->
                        val path = getPath(e.accountId)
                        path.any { it.id == accountPayable.id }
                    }
                    if (entries.size == 1 && entries.single().amountInCents > 0) {
                        b to entries.single()
                    } else null
                }.forEach { (c, e) ->
                    val otherSide = bookings
                        .filterNot { it.id == c.id }
                        .singleOrNull { b ->
                            val entries = b.entries.filter { e ->
                                val path = getPath(e.accountId)
                                path.any { it.id == accountPayable.id }
                            }
                            entries.size == 1 && entries.single().amountInCents < 0 && (entries.single().amountInCents * -1 == e.amountInCents)
                        }

                    if (otherSide != null) {
                        candidates.add(listOf(
                            c,
                            otherSide
                        ))
                    }
                }

                bookings.mapNotNull { b ->
                    val entries = b.entries.filter { e ->
                        val path = getPath(e.accountId)
                        path.any { it.id == accountReceivable.id }
                    }
                    if (entries.size == 1 && entries.single().amountInCents < 0) {
                        b to entries.single()
                    } else null
                }.forEach { (c, e) ->
                    val otherSide = bookings
                        .filterNot { it.id == c.id }
                        .singleOrNull { b ->
                            val entries = b.entries.filter { e ->
                                val path = getPath(e.accountId)
                                path.any { it.id == accountReceivable.id }
                            }
                            entries.size == 1 && entries.single().amountInCents > 0 && (entries.single().amountInCents * -1 == e.amountInCents)
                        }

                    if (otherSide != null) {
                        candidates.add(listOf(
                            c,
                            otherSide
                        ))
                    }
                }
            }

            candidates.forEach { toBeMerged ->
                datasource.connection.use { c ->
                    c.autoCommit = false

                    val desc = toBeMerged.mapNotNull { it.description }.distinct()
                    if (!(desc.isEmpty() || desc.size == 1)) {
                        error("description not the same")
                    }

                    toBeMerged.forEach { b ->
                        bookingRepository.deleteBooking(c, realm.id, b.id)
                    }

                    bookingRepository.insert(c, AddBooking(
                        realm.id,
                        toBeMerged.first().description,
                        toBeMerged.first().datetime,
                        toBeMerged.flatMap { it.entries }.map {
                            AddBookingEntry(
                                it.description,
                                it.accountId,
                                it.amountInCents
                            )
                        }
                    ))

                    c.commit()
                }
            }
        }

        executorService.shutdown()
    }

}