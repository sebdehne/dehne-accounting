package com.dehnes.accounting.database

import com.dehnes.accounting.api.ChangeEvent
import com.dehnes.accounting.api.DatabaseBackupChanged
import com.dehnes.accounting.api.DatabaseRestored
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

class DatabaseBackupService(
    private val dataSource: DataSource,
    private val changelog: Changelog,
) {

    private val backupDir = File(System.getProperty("BACKUP_DIR", "./backups"))

    init {
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        check(backupDir.exists()) { "Backup directory does not exist." }
    }

    fun listBackups(): List<String> = backupDir.listFiles()!!.map { it.name }.sorted()

    fun createBackup() {
        dataSource.readTx {
            it.createStatement().use {
                it.executeUpdate("backup to ${File(backupDir, newBackup()).absoluteFile}")
            }
        }
        changelog.handleChanges(setOf(ChangeEvent(DatabaseBackupChanged)))
    }

    fun restoreBackup(name: String) {

        val dbFile = File(backupDir, name)
        check(dbFile.exists()) { "Cannot restore backup $name, file not found" }

        dataSource.writeTx {
            it.autoCommit = true
            it.createStatement().use {
                it.executeUpdate("restore from ${dbFile.absoluteFile}")
            }
        }
        SchemaHandler.initSchema(dataSource)
        changelog.handleChanges(setOf(ChangeEvent(DatabaseRestored)))
    }

    fun dropBackup(name: String) {
        val dbFile = File(backupDir, name)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        changelog.handleChanges(setOf(ChangeEvent(DatabaseBackupChanged)))
    }

    private fun newBackup() = "backup-${Instant.now().truncatedTo(ChronoUnit.SECONDS)}.db"

}