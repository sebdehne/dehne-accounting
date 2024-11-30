package com.dehnes.accounting.bank.importers

import java.io.InputStream
import java.time.Instant
import kotlin.reflect.KClass

interface Importer {

    fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    )
}

enum class SupportedImporters(
    val klazz: KClass<out Importer>
) {
    DanskeBankCsvExportImporter(com.dehnes.accounting.bank.importers.DanskeBankCsvExportImporter::class),
    SBanken(DnbCsvImporter::class),
    BulderImporter(BulderCsvImporter::class),
    HandelsbankenImporter(HandelsbankenCsvImporter::class),
    NordeaImporter(NordeaCsvImporter::class),
}

data class BankTransactionImportRecord(
    val description: String?,
    val datetime: Instant,
    val amountInCents: Long,
    val otherAccountNumber: String?,
)

