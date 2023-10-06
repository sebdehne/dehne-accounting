package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.utils.CsvParser.parseLine
import com.dehnes.accounting.utils.DateTimeUtils
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SBankenCsvExportImporter: Importer {

    val supportedHeaderLines = listOf(
        listOf(
            "BOKFØRINGSDATO",
            "RENTEDATO",
            "ARKIVREFERANSE",
            "MOTKONTO",
            "TYPE",
            "TEKST",
            "UT FRA KONTO",
            "INN PÅ KONTO",
        )
    )

    override fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    ) {

        val records = mutableListOf<BankTransactionImportRecord>()

        BufferedReader(InputStreamReader(dataSource, StandardCharsets.ISO_8859_1)).use { reader ->
            // skip forward until the first headerLine

            var detectedHeader: List<String>? = null

            while(true) {
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


            while(true) {
                val line = reader.readLine() ?: break
                val parts = line.parseLine()

                if (parts.size != detectedHeader.size) continue

                val datoStr = getValue(parts, "BOKFØRINGSDATO") ?: continue
                val date = LocalDate.parse(datoStr)
                val text = getValue(parts, "TEKST")

                val debit = getValue(parts, "INN PÅ KONTO")?.parseAmount()
                val credit = getValue(parts, "UT FRA KONTO")?.parseAmount()

                records.add(
                    BankTransactionImportRecord(
                        text,
                        date.atStartOfDay().atZone(DateTimeUtils.zoneId).toInstant(),
                        debit ?: (credit!! * -1L)
                    )
                )
            }
        }

        records.sortedBy { it.datetime }.forEach(onNewRecord)
    }

    private fun String.parseAmount(): Long {
        var str = this

        val parts = str.split(",")
        check(parts.size == 2)
        return ((parts[0].toLong() * 100) + parts[1].toLong())
    }
}