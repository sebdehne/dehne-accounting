package com.dehnes.accounting.utils

object CsvParser {
    fun String.parseLine(delimiter: String = ";"): List<String> = this.split(delimiter).map { it.trim() }.map {
        if (it.startsWith("\"")) {
            it.drop(1)
        } else it
    }.map {
        if (it.endsWith("\""))
            it.dropLast(1)
        else it
    }.map { it.replace("\"\"", "\"") }
}