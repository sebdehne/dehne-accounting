package com.dehnes.accounting.utils

import mu.KotlinLogging

val errorLogger = KotlinLogging.logger("com.dehnes.accounting.utils.ErrorLogger")

fun withLogging(fn: () -> Unit) = Runnable {
    try {
        fn()
    } catch (t: Throwable) {
        errorLogger.error(t) { "" }
    }
}