package com.dehnes.accounting

import com.dehnes.accounting.api.ReadService
import com.dehnes.accounting.services.TransactionMatchingService
import com.dehnes.accounting.bank.importers.BankTransactionImportService
import com.dehnes.accounting.database.Changelog
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.SchemaHandler
import com.dehnes.accounting.rapports.RapportService
import com.dehnes.accounting.services.*
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sqlite.SQLiteConfig
import java.util.concurrent.Executors
import javax.sql.DataSource
import kotlin.reflect.KClass


fun objectMapper() = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

class Configuration {
    var beans = mutableMapOf<KClass<*>, Any>()


    fun init() {

        val executorService = Executors.newCachedThreadPool()
        val objectMapper = objectMapper()

        val datasource = datasourceSetup(dbFile())
        SchemaHandler.initSchema(datasource)

        val changelog = Changelog(objectMapper)
        val repository = Repository(changelog, objectMapper)
        val categoryReadService = CategoryReadService(repository, datasource)
        val userService = UserService(datasource)
        val bookingReadService = BookingReadService(repository, userService)
        val bankService = BankService(bookingReadService, repository, datasource)
        val rapportService = RapportService(repository, categoryReadService)
        val bankTransactionImportService = BankTransactionImportService(datasource, repository, bookingReadService)
        val transactionMatchingService = TransactionMatchingService(repository, datasource, bookingReadService)
        val userStateService = UserStateService(datasource, repository)
        val readService = ReadService(
            bookingReadService,
            bankService,
            executorService,
            userService,
            rapportService,
            categoryReadService,
            userStateService,
            transactionMatchingService,
            datasource
        )
        val bookingWriteService = BookingWriteService(repository, datasource, bookingReadService, categoryReadService)


        beans[BankService::class] = bankService
        beans[ObjectMapper::class] = objectMapper
        beans[ReadService::class] = readService
        beans[UserService::class] = userService
        beans[BankTransactionImportService::class] = bankTransactionImportService
        beans[TransactionMatchingService::class] = transactionMatchingService
        beans[UserStateService::class] = userStateService
        beans[BookingWriteService::class] = bookingWriteService
        beans[CategoryWriteService::class] = CategoryWriteService(repository, datasource, bookingReadService)
    }

    inline fun <reified T> getBeanNull(): T? {
        val kClass = T::class
        return (beans[kClass]) as T
    }

    inline fun <reified T> getBean(): T {
        val kClass = T::class
        return (beans[kClass] ?: error("No such bean $kClass")) as T
    }
}

fun dbFile(path: String = ".") = System.getProperty("SQLITE_FILE", "$path/sql.db")

fun datasourceSetup(sqliteFile: String): DataSource {

    val sqLiteConfig = SQLiteConfig()
    sqLiteConfig.enforceForeignKeys(true)

    val config = HikariConfig()
    config.jdbcUrl = "jdbc:sqlite:$sqliteFile"
    config.schema = "dehne-accounting"
    config.driverClassName = "org.sqlite.JDBC"
    config.isAutoCommit = false

    sqLiteConfig.toProperties().forEach { key, value ->
        config.addDataSourceProperty(key.toString(), value)
    }

    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "250")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

    val ds = HikariDataSource(config)

    SchemaHandler.initSchema(ds)

    return ds
}