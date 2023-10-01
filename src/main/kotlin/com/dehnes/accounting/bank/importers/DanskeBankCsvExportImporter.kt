package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.datasourceSetup
import com.dehnes.accounting.objectMapper
import com.dehnes.accounting.utils.CsvParser.parseLine
import com.dehnes.smarthome.utils.DateTimeUtils.zoneId
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DanskeBankCsvExportImporter : Importer {

    private val expectedHeaders = listOf(
        "Dato",
        "Tekst",
        "Beløp",
        "Saldo",
        "Status",
        "Avstemt",
    )

    private val datoFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    ) {


        BufferedReader(InputStreamReader(dataSource, StandardCharsets.ISO_8859_1)).use { reader ->
            val headerLine = reader.readLine().parseLine()
            check(headerLine == expectedHeaders) { "Unsupported headers detected: $headerLine" }

            while (true) {
                val line = reader.readLine() ?: break
                val parts = line.parseLine()


                val date = LocalDate.parse(parts[0], datoFormat)
                val text = parts[1]
                val status = parts[4]

                if (status != "Utført") continue

                val amount = parts[2].parseAmount()
                val saldo = parts[3].parseAmount()
                val avstemt = parts[5] == "Ja"

                if (avstemt) continue

                onNewRecord(
                    BankTransactionImportRecord(
                        text,
                        date.atStartOfDay().atZone(zoneId).toInstant(),
                        amount,
                    )
                )
            }
        }

    }

    private fun String.parseAmount(): Long {
        var str = this.replace(".", "")
        val negativ = if (str.startsWith("-")) {
            str = str.drop(1)
            -1
        } else 1

        val parts = str.split(",")
        check(parts.size == 2)
        return ((parts[0].toLong() * 100) + parts[1].toLong()) * negativ
    }
}
