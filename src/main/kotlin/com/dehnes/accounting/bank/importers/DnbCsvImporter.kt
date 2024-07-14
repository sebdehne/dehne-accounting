package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.utils.CsvParser.parseLine
import com.dehnes.accounting.utils.DateTimeUtils
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DnbCsvImporter : Importer {

    val supportedHeaderLines = listOf(
        listOf(
            "Dato",
            "Forklaring",
            "Rentedato",
            "Ut fra konto",
            "Inn på konto",
        )
    )

    override fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    ) {

        val records = mutableListOf<BankTransactionImportRecord>()

        BufferedReader(InputStreamReader(dataSource, StandardCharsets.UTF_8)).use { reader ->
            // skip forward until the first headerLine

            var detectedHeader: List<String>? = null

            while (true) {
                val readLine = reader.readLine() ?: break
                val headerLine = readLine.parseLine()

                if (headerLine.isEmpty() || headerLine.first().isBlank())
                    continue

                if (headerLine in supportedHeaderLines) {
                    detectedHeader = supportedHeaderLines.first { it == headerLine }
                    break
                }
            }
            if (detectedHeader == null) {
                error("No header line found");
            }

            val getValue = { line: List<String>, field: String ->
                line[detectedHeader.indexOf(field)]
                    .ifBlank { null }
            }


            while (true) {
                val line = reader.readLine() ?: break
                val parts = line.parseLine()

                if (parts.size != detectedHeader.size) continue

                val datoStr = getValue(parts, "Rentedato") ?: continue
                val date = LocalDate.parse(datoStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                val text = getValue(parts, "Forklaring")

                val debit = getValue(parts, "Inn på konto")?.parseAmount()
                val credit = getValue(parts, "Ut fra konto")?.parseAmount()

                records.add(
                    BankTransactionImportRecord(
                        text,
                        date.atStartOfDay().atZone(DateTimeUtils.zoneId).toInstant(),
                        debit ?: (credit!! * -1L),
                        null
                    )
                )
            }
        }

        records.sortedBy { it.datetime }.forEach(onNewRecord)
    }

    private fun String.parseAmount(): Long {
        var str = this

        val parts = str.split(".")
        if (parts.size == 1) {
            return parts[0].toLong() * 100
        }
        check(parts.size == 2)

        val main = parts[0]
        var decimals = parts[1]
        if (decimals.length == 1) {
            decimals = "${decimals}0"
        }
        check(decimals.length == 2)

        return ((main.toLong() * 100) + decimals.toLong())
    }
}