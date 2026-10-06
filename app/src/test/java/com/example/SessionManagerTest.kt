package com.example

import com.example.security.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    @Test
    fun locksAfterFiveMinutesOfInactivity() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val manager = SessionManager(scope, timeoutMillis = 5 * 60 * 1000L)
        val start = manager.lastActivityTime

        assertFalse(manager.isSessionLocked.value)
        assertFalse(manager.checkInactivity(start + (5 * 60 * 1000L) - 1))
        assertTrue(manager.checkInactivity(start + (5 * 60 * 1000L)))
        assertTrue(manager.isSessionLocked.value)

        manager.stop()
    }

    @Test
    fun activityBeforeTimeoutPreventsLockAndUnlockClearsLock() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val manager = SessionManager(scope, timeoutMillis = 5 * 60 * 1000L)
        val start = manager.lastActivityTime

        manager.onUserActivity(start + 4 * 60 * 1000L)
        assertFalse(manager.checkInactivity(start + 4 * 60 * 1000L + 59_999L))

        manager.lockSession()
        assertTrue(manager.isSessionLocked.value)
        manager.unlockSession()
        assertFalse(manager.isSessionLocked.value)

        manager.stop()
    }

    @Test
    fun repeatedInactivityChecksDoNotRelockUnlockedSessionImmediately() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val manager = SessionManager(scope, timeoutMillis = 1_000L)
        val start = manager.lastActivityTime

        assertTrue(manager.checkInactivity(start + 1_000L))
        manager.unlockSession()
        assertFalse(manager.isSessionLocked.value)
        assertFalse(manager.checkInactivity(System.currentTimeMillis()))

        manager.stop()
    }
}
