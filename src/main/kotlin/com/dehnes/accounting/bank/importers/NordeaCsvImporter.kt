package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.utils.CsvParser.parseLine
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NordeaCsvImporter: Importer {

    private val supportedFormats: List<List<String>> = listOf(
        listOf(
            "Bokføringsdato",
            "Beløp",
            "Avsender",
            "Mottaker",
            "Navn",
            "Tittel",
            "Valuta",
            "Betalingstype",
        ),
    )

    private val datoFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    override fun import(
        dataSource: InputStream,
        filename: String,
        onNewRecord: (record: BankTransactionImportRecord) -> Unit
    ) {

        val records = mutableListOf<BankTransactionImportRecord>()

        BufferedReader(InputStreamReader(dataSource, StandardCharsets.UTF_8)).use { reader ->
            val headerLine = reader.readLine().parseLine().map {
                it.replace("\uFEFF", "")
            }

            val supportedFormat = supportedFormats
                .firstOrNull { it == headerLine } ?: error("Unsupported headers detected: $headerLine")

            fun getValue(line: List<String>, vararg field: String) = line[supportedFormat.indexOfFirst {
                it in field
            }]

            while (true) {
                val line = reader.readLine() ?: break
                val parts = line.parseLine()

                val datoStr = getValue(parts, "Bokføringsdato")
                if (datoStr.lowercase() == "reservert") continue
                val valuta = getValue(parts, "Valuta")
                check(valuta == "NOK") {"Unsupported valuta"}

                val date = LocalDate.parse(datoStr, datoFormat)
                val text = getValue(parts, "Tittel")
                val amount = getValue(parts, "Beløp").parseAmount()

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