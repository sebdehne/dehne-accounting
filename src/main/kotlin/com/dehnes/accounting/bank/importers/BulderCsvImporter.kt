package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.utils.CsvParser.parseLine
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BulderCsvImporter :Importer {

    private val supportedFormats: List<List<String>> = listOf(
        listOf(
            "Dato",
            "Inn på konto",
            "Ut fra konto",
            "Til konto",
            "Til kontonummer",
            "Fra konto",
            "Fra kontonummer",
            "Type",
            "Tekst",
            "KID",
            "Hovedkategori",
            "Underkategori",
        ),
    )

    private val datoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    ) {

        val records = mutableListOf<BankTransactionImportRecord>()

        BufferedReader(InputStreamReader(dataSource, StandardCharsets.UTF_8)).use { reader ->
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
                val innPaaKonto = getValue(parts, "Inn på konto").ifBlank { null }
                val utFraPaaKonto = getValue(parts, "Ut fra konto").ifBlank { null }
                val type = getValue(parts, "Type").ifBlank { null }
                val tekst = getValue(parts, "Tekst").ifBlank { null }
                val kid = getValue(parts, "KID").ifBlank { null }
                val tilKontonummer = getValue(parts, "Til kontonummer")
                val fraKontonummer = getValue(parts, "Fra kontonummer")

                val amount = if (innPaaKonto != null) {
                    innPaaKonto.parseAmount()
                } else if (utFraPaaKonto != null) {
                    utFraPaaKonto.parseAmount() * -1
                } else {
                    continue
                }

                val text = listOfNotNull(
                    tekst,
                    kid
                ).joinToString("-")

                records.add(
                    BankTransactionImportRecord(
                        text,
                        date.atStartOfDay().atZone(zoneId).toInstant(),
                        amount,
                        if (amount > 0) fraKontonummer else tilKontonummer,
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