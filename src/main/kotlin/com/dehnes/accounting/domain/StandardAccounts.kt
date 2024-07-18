package com.dehnes.accounting.domain

import java.util.*

enum class StandardAccount(
    val parent: StandardAccount? = null
) {
    Asset,
    Liability,
    Equity,
    Income,
    Expense,
    AccountPayable(Liability),
    AccountReceivable(Asset),
    OpeningBalances(Equity),
    OtherBankTransfers(Equity),
    BankAccountAsset(Asset),
    BankAccountLiability(Liability),
    ;

    fun toAccountId(realmId: String) = UUID.nameUUIDFromBytes("$realmId-%$this".toByteArray()).toString()

}