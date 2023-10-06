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
        )
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

            val getValue = { line: List<String>, field: String ->
                line[supportedFormat.indexOf(field)]
            }

            while (true) {
                val line = reader.readLine() ?: break
                val parts = line.parseLine()

                val date = LocalDate.parse(getValue(parts, "Dato"), datoFormat)
                val text = getValue(parts, "Tekst")
                val status = getValue(parts, "Status")

                if (status != "Utført") continue

                val amount = getValue(parts, "Beløp").parseAmount()
                //val saldo = getValue(parts, "Saldo").parseAmount()
                val avstemt = getValue(parts, "Avstemt") == "Ja"

                if (avstemt) continue

                records.add(
                    BankTransactionImportRecord(
                        text,
                        date.atStartOfDay().atZone(zoneId).toInstant(),
                        amount,
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
        return ((parts[0].toLong() * 100) + parts[1].toLong()) * negativ
    }
}
