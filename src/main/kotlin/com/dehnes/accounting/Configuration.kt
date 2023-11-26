package com.dehnes.accounting

import com.dehnes.accounting.api.ReadService
import com.dehnes.accounting.bank.importers.BankTransactionImportService
import com.dehnes.accounting.database.*
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

        val changelog = Changelog(datasource)
        val userRepository = UserRepository(objectMapper, changelog)
        val accountsRepository = AccountsRepository(changelog)
        val realmRepository = RealmRepository(accountsRepository, changelog)
        val authorizationService = AuthorizationService(userRepository, realmRepository)
        val userService = UserService(datasource, userRepository, authorizationService, changelog)
        val userStateRepository = UserStateRepository(objectMapper, changelog)
        val bookingRepository = BookingRepository(realmRepository, changelog, datasource)
        val bankRepository = BankRepository(datasource, changelog)
        val bankAccountRepository = BankAccountRepository(changelog)
        val unbookedTransactionRepository = UnbookedTransactionRepository(changelog)
        val bankAccountService = BankAccountService(
            bookingRepository,
            bankRepository,
            bankAccountRepository,
            datasource,
            authorizationService,
            unbookedTransactionRepository,
            changelog
        )


        val bankTransactionImportService = BankTransactionImportService(
            authorizationService,
            bankAccountRepository,
            bankAccountService,
            unbookedTransactionRepository,
            bankRepository,
            changelog
        )
        val unbookedBankTransactionMatcherRepository = UnbookedBankTransactionMatcherRepository(objectMapper, changelog)
        val unbookedBankTransactionMatcherService = UnbookedBankTransactionMatcherService(
            unbookedTransactionRepository,
            authorizationService,
            unbookedBankTransactionMatcherRepository,
            datasource,
            changelog,
            bookingRepository
        )
        val bookingService = BookingService(datasource, bookingRepository, authorizationService, changelog)
        val accountService = AccountService(
            authorizationService,
            accountsRepository,
            bookingRepository,
            unbookedBankTransactionMatcherRepository,
            changelog,
        )

        val userStateService = UserStateService(datasource, userStateRepository, userService, changelog)

        val readService = ReadService(
            executorService,
            userStateService,
            userService,
            datasource,
            OverviewRapportService(datasource, bookingRepository, accountsRepository),
            bankAccountService,
            accountsRepository,
            unbookedBankTransactionMatcherService,
            bookingService,
            changelog
        )


        beans[ObjectMapper::class] = objectMapper
        beans[ReadService::class] = readService
        beans[UserService::class] = userService
        beans[BankTransactionImportService::class] = bankTransactionImportService
        beans[BankAccountService::class] = bankAccountService
        beans[UserStateService::class] = userStateService
        beans[UnbookedBankTransactionMatcherService::class] = unbookedBankTransactionMatcherService
        beans[BookingService::class] = bookingService
        beans[AccountService::class] = accountService
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
    config.minimumIdle = 10
    config.maximumPoolSize = 50

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