package com.example.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionManager(
    private val scope: CoroutineScope,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L // 5 minutes
    }

    private var _lastActivityTime = System.currentTimeMillis()
    val lastActivityTime: Long get() = _lastActivityTime

    private val _isSessionLocked = MutableStateFlow(false)
    val isSessionLocked: StateFlow<Boolean> = _isSessionLocked.asStateFlow()

    private var tickerJob: Job? = null

    init {
        startInactivityTimer()
    }

    fun onUserActivity(now: Long = System.currentTimeMillis()) {
        _lastActivityTime = now
    }

    fun lockSession() {
        _isSessionLocked.value = true
    }

    fun unlockSession() {
        _lastActivityTime = System.currentTimeMillis()
        _isSessionLocked.value = false
    }

    fun checkInactivity(now: Long = System.currentTimeMillis()): Boolean {
        if (!_isSessionLocked.value && (now - _lastActivityTime >= timeoutMillis)) {
            lockSession()
            return true
        }
        return false
    }

    private fun startInactivityTimer() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(10_000L) // Check every 10 seconds
                checkInactivity(System.currentTimeMillis())
            }
        }
    }

    fun stop() {
        tickerJob?.cancel()
    }
}
