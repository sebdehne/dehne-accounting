package com.dehnes.accounting.bank.importers

import java.io.InputStream

class HandelsbankenCsvImporter: Importer {
    override fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    ) {
        TODO("Not yet implemented")
    }
}