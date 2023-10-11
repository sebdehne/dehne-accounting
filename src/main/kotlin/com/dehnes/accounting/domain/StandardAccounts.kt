package com.dehnes.accounting.domain

import com.dehnes.accounting.database.AccountDto
import com.dehnes.accounting.utils.KMyMoneyImporter
import java.util.UUID

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
    fun toAccountDto(realmId: String) = AccountDto(
        toAccountId(realmId),
        this.name,
        null,
        realmId,
        parent?.toAccountId(realmId),
        null
    )

}