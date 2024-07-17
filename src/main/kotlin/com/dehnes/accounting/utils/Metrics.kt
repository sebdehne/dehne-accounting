package com.dehnes.accounting.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration

object Metrics {

    private val logger = KotlinLogging.logger {  }

    fun <T> logTimed(info: String, fn: () -> T): T {
        logger.info { "$info started ..." }
        val start = System.nanoTime()
        try {
            return fn()
        } finally {
            logger.info { "$info finished in ${Duration.ofNanos(System.nanoTime() - start).toMillis()}ms" }
        }
    }
}

