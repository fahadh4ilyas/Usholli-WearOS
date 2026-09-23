package com.mrhabibi.usholli.wear.util

import kotlinx.coroutines.delay

/**
 * Retries [block] up to [maxAttempts] times, waiting with exponential backoff
 * between attempts ([initialDelayMs], doubling up to [maxDelayMs]).
 * Rethrows the last exception if all attempts fail.
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1_000L,
    maxDelayMs: Long = 10_000L,
    block: suspend () -> T,
): T {
    var delayMs = initialDelayMs
    var lastException: Exception? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
            }
        }
    }
    throw lastException ?: IllegalStateException("retryWithBackoff failed")
}
