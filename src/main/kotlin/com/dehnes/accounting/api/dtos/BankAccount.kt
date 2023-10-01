package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.BankDto
import com.dehnes.accounting.domain.InformationElement
import java.time.Instant

data class BankAccountView(
    override val id: String,
    override val name: String,
    override val description: String?,
    val bank: BankView,
    val closed: Boolean,
    val accountNumber: String,
    val transactionsCounterUnbooked: Long,
    val currentBalance: Long,
) : InformationElement()

data class BankView(
    override val id: String,
    override val name: String,
    override val description: String?,
) : InformationElement() {
    companion object {
        fun fromDto(dto: BankDto) = BankView(
            dto.id,
            dto.name,
            dto.description,
        )
    }
}

data class BankTransactionsRequest(
    val bankAccountId: String,
    val from: Instant,
    val toExcluding: Instant,
)

data class BankTransactionRequest(
    val ledgerId: String,
    val bankAccountId: String,
    val transactionId: Long,
)

data class BankAccountTransactionView(
    val id: Long,
    val description: String?,
    val datetime: Instant,
    val amount: Long,
    val balance: Long,
    val matched: Boolean,
)