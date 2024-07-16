package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.utils.CsvParser.parseLine
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class DanskeBankCsvExportImporter : Importer {

    private val supportedFormats: List<List<String>> = listOf(
        listOf(
            "Dato",
            "Tekst",
            "Beløp",
            "Saldo",
            "Status",
            "Avstemt",
        ),
        listOf(
            "Dato",
            "Kategori",
            "Underkategori",
            "Tekst",
            "Beløp",
            "Saldo",
            "Status",
            "Avstemt",
        ),
        listOf(
            "Date",
            "Text",
            "Amount",
            "Balance",
            "Status",
            "Reconciled",
        ),
    )


    private val datoFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    ) {

        val records = mutableListOf<BankTransactionImportRecord>()

        BufferedReader(InputStreamReader(dataSource, StandardCharsets.ISO_8859_1)).use { reader ->
            val headerLine = reader.readLine().parseLine()

            val supportedFormat = supportedFormats
                .firstOrNull { it == headerLine } ?: error("Unsupported headers detected: $headerLine")

            fun getValue(line: List<String>, vararg field: String) = line[supportedFormat.indexOfFirst {
                it in field
            }]

            while (true) {
                val line = reader.readLine() ?: break
                val parts = line.parseLine()

                val date = LocalDate.parse(getValue(parts, "Dato", "Date"), datoFormat)
                val text = getValue(parts, "Tekst", "Text")
                val status = getValue(parts, "Status")

                if (status !in listOf("Utført", "Executed")) continue

                val amount = getValue(parts, "Beløp", "Amount").parseAmount()
                val avstemt = getValue(parts, "Avstemt", "Reconciled").let {
                    it == "Ja" || it == "Yes"
                }

                if (avstemt) continue

                records.add(
                    BankTransactionImportRecord(
                        text,
                        date.atStartOfDay().atZone(zoneId).toInstant(),
                        amount,
                        null,
                    )
                )
            }
        }

        // export from online banking has ascending order, but
        // export from mobile app has descending order :/
        records.sortedBy { it.datetime }.forEach(onNewRecord)
    }

    private fun String.parseAmount(): Long {
        var str = this
            .replace(".", "")
            .replace(" ", "")
        val negativ = if (str.startsWith("-")) {
            str = str.drop(1)
            -1
        } else 1

        val parts = str.split(",")
        check(parts.size == 2)

        val main = parts[0]
        var decimals = parts[1]
        if (decimals.length == 1) {
            decimals = "${decimals}0"
        }
        check(decimals.length == 2)

        return ((main.toLong() * 100) + decimals.toLong()) * negativ
    }
}
