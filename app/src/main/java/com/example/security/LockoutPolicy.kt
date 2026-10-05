package com.example.security

data class LockoutPolicy(
    val maxFailedAttempts: Int = 5,
    val lockDurationMillis: Long = 5 * 60 * 1000L // 5 minutes
) {
    fun isLocked(lockedUntil: Long?, currentTime: Long = System.currentTimeMillis()): Boolean {
        return lockedUntil != null && lockedUntil > currentTime
    }

    fun remainingLockTimeMillis(lockedUntil: Long?, currentTime: Long = System.currentTimeMillis()): Long {
        return if (isLocked(lockedUntil, currentTime)) {
            (lockedUntil!! - currentTime).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    fun remainingLockTimeSeconds(lockedUntil: Long?, currentTime: Long = System.currentTimeMillis()): Long {
        val millis = remainingLockTimeMillis(lockedUntil, currentTime)
        return if (millis > 0) (millis + 999) / 1000 else 0L
    }

    fun calculateLockout(newFailedAttempts: Int, currentTime: Long = System.currentTimeMillis()): Long? {
        return if (newFailedAttempts >= maxFailedAttempts) {
            currentTime + lockDurationMillis
        } else {
            null
        }
    }
}
