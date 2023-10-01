package com.dehnes.accounting.utils

import mu.KLogger

fun wrap(logger: KLogger, r: Runnable) = Runnable {
    try {
        r.run()
    } catch (e: Throwable) {
        logger.error(e) { "" }
    }
}