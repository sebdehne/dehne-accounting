package com.dehnes.accounting.utils

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock


interface LockingService {

    fun runLocked(
        lock: String,
        timeout: Duration = Duration.ofMinutes(1),
        fn: () -> Unit
    ): Boolean

}

class InMemoryLockingService : LockingService {

    private val locks = mutableMapOf<String, ReentrantLock>()

    override fun runLocked(
        lock: String,
        timeout: Duration,
        fn: () -> Unit
    ): Boolean {

        val lock = synchronized(locks) {
            locks.getOrPut(lock) { ReentrantLock() }
        }

        var gotLock = false

        if (lock.tryLock(
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
            )
        ) {
            try {
                gotLock = true
                fn()
            } finally {
                lock.unlock()
            }
        }

        return gotLock
    }

}