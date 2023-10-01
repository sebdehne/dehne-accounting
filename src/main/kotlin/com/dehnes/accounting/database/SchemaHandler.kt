package com.dehnes.accounting.database

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import javax.sql.DataSource


object SchemaHandler {

    fun initSchema(dataSource: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(Location("classpath:db/"))
            .load()
        flyway.migrate()
    }

}