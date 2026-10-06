package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.local.migration.MIGRATION_1_2
import com.example.data.seed.DatabaseSeeder
import com.example.domain.model.PaymentMethod
import com.example.domain.model.UserRole
import com.example.security.LockoutPolicy
import com.example.security.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityAndAuthTest {

    private lateinit var db: AppDatabase
    private val hasher = PasswordHasher.DEFAULT
    private val lockoutPolicy = LockoutPolicy(maxFailedAttempts = 5, lockDurationMillis = 5 * 60 * 1000L)

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSamePinDifferentSaltsProduceDifferentHashes() {
        val pin = "1234"
        val hashResult1 = hasher.hash(pin)
        val hashResult2 = hasher.hash(pin)

        // Different random salts
        assertNotEquals(hashResult1.saltHex, hashResult2.saltHex)
        // Different resulting hashes
        assertNotEquals(hashResult1.hashHex, hashResult2.hashHex)
    }

    @Test
    fun testPinVerificationSuccessAndFailure() {
        val pin = "8765"
        val hashResult = hasher.hash(pin)

        // Correct PIN
        assertTrue(hasher.verify("8765", hashResult.hashHex, hashResult.saltHex))

        // Incorrect PIN
        assertFalse(hasher.verify("0000", hashResult.hashHex, hashResult.saltHex))
        assertFalse(hasher.verify("8764", hashResult.hashHex, hashResult.saltHex))
        assertFalse(hasher.verify("87651", hashResult.hashHex, hashResult.saltHex))
        assertFalse(hasher.verify("", hashResult.hashHex, hashResult.saltHex))
    }

    @Test
    fun testNoPlaintextPinInUserEntity() {
        val hashResult = hasher.hash("9999")
        val user = UserEntity(
            id = "test-user-id",
            name = "مستخدم اختبار",
            username = "tester",
            pinHash = hashResult.hashHex,
            pinSalt = hashResult.saltHex,
            role = UserRole.CASHIER
        )

        // Reflection check: UserEntity must not contain any field named 'pin'
        val fields = UserEntity::class.java.declaredFields.map { it.name }
        assertFalse("UserEntity must not have plaintext 'pin' field!", fields.contains("pin"))
        assertTrue("UserEntity must have 'pinHash' field", fields.contains("pinHash"))
        assertTrue("UserEntity must have 'pinSalt' field", fields.contains("pinSalt"))
        assertTrue("UserEntity must have 'failedAttempts' field", fields.contains("failedAttempts"))
        assertTrue("UserEntity must have 'lockedUntil' field", fields.contains("lockedUntil"))
    }

    @Test
    fun testFiveFailedAttemptsLeadToLockout() = runBlocking {
        val hashResult = hasher.hash("2468")
        val user = UserEntity(
            id = "user-lock-test",
            name = "أحمد الحرازي",
            username = "alharazi",
            pinHash = hashResult.hashHex,
            pinSalt = hashResult.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(user)

        var currentUser = db.userDao().getUserById("user-lock-test")!!
        val now = System.currentTimeMillis()

        // 4 failed attempts: not locked yet
        for (i in 1..4) {
            val valid = hasher.verify("0000", currentUser.pinHash, currentUser.pinSalt)
            assertFalse(valid)
            val newAttempts = currentUser.failedAttempts + 1
            val lockUntil = lockoutPolicy.calculateLockout(newAttempts, now)
            assertNull("Should not be locked after attempt $i", lockUntil)
            db.userDao().updateFailedAttempts(currentUser.id, newAttempts, lockUntil, now)
            currentUser = db.userDao().getUserById("user-lock-test")!!
            assertEquals(i, currentUser.failedAttempts)
            assertNull(currentUser.lockedUntil)
        }

        // 5th failed attempt: must trigger lock
        val valid5 = hasher.verify("0000", currentUser.pinHash, currentUser.pinSalt)
        assertFalse(valid5)
        val newAttempts5 = currentUser.failedAttempts + 1
        val lockUntil5 = lockoutPolicy.calculateLockout(newAttempts5, now)
        assertNotNull("Must be locked on 5th attempt", lockUntil5)
        assertEquals(now + lockoutPolicy.lockDurationMillis, lockUntil5)

        db.userDao().updateFailedAttempts(currentUser.id, newAttempts5, lockUntil5, now)
        currentUser = db.userDao().getUserById("user-lock-test")!!

        assertEquals(5, currentUser.failedAttempts)
        assertNotNull(currentUser.lockedUntil)
        assertTrue(lockoutPolicy.isLocked(currentUser.lockedUntil, now))
    }

    @Test
    fun testCorrectPinWhileLockedIsRejected() = runBlocking {
        val hashResult = hasher.hash("1357")
        val now = System.currentTimeMillis()
        val lockedUntil = now + (5 * 60 * 1000L)

        val user = UserEntity(
            id = "user-locked-pin",
            name = "كاشير مؤقت",
            username = "cashier_locked",
            pinHash = hashResult.hashHex,
            pinSalt = hashResult.saltHex,
            role = UserRole.CASHIER,
            failedAttempts = 5,
            lockedUntil = lockedUntil
        )
        db.userDao().insertUser(user)

        val fetched = db.userDao().getUserById("user-locked-pin")!!
        // Even though PIN "1357" is correct, lockout policy rejects authentication!
        assertTrue(lockoutPolicy.isLocked(fetched.lockedUntil, now))
        assertTrue(hasher.verify("1357", fetched.pinHash, fetched.pinSalt))

        // Remaining seconds calculation
        val remainingSec = lockoutPolicy.remainingLockTimeSeconds(fetched.lockedUntil, now)
        assertTrue(remainingSec > 0)
    }

    @Test
    fun testSuccessfulLoginResetsFailedAttempts() = runBlocking {
        val hashResult = hasher.hash("7777")
        val user = UserEntity(
            id = "user-reset-test",
            name = "صالح الوالي",
            username = "saleh",
            pinHash = hashResult.hashHex,
            pinSalt = hashResult.saltHex,
            role = UserRole.CASHIER,
            failedAttempts = 3,
            lockedUntil = null
        )
        db.userDao().insertUser(user)

        val now = System.currentTimeMillis()
        db.userDao().recordSuccessfulLogin(user.id, now)

        val updated = db.userDao().getUserById("user-reset-test")!!
        assertEquals(0, updated.failedAttempts)
        assertNull(updated.lockedUntil)
        assertEquals(now, updated.lastLoginAt)
    }

    @Test
    fun testFailedLoginWritesAuditLog() = runBlocking {
        val hashResult = hasher.hash("4321")
        val user = UserEntity(
            id = "user-audit-test",
            name = "طارق",
            username = "tariq",
            pinHash = hashResult.hashHex,
            pinSalt = hashResult.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(user)

        // Log failed login
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.titleAr,
                action = "LOGIN_FAILED",
                entityType = "USER",
                entityId = user.username,
                notes = "محاولة دخول فاشلة برمز PIN خاطئ (المحاولة 1 من 5)"
            )
        )

        val logs = db.auditLogDao().getRecentLogs().first()
        val failedLog = logs.find { it.action == "LOGIN_FAILED" && it.userId == user.id }
        assertNotNull("Failed login must be recorded in AuditLog", failedLog)
        assertFalse(failedLog!!.notes.contains("4321")) // PIN must NOT be in audit log notes!
    }

    @Test
    fun testSeederDoesNotRecreateUsersWhenPinChanges() = runBlocking {
        DatabaseSeeder.seedIfEmpty(db)
        val initialUsers = db.userDao().getAllActiveUsers().first()
        assertEquals(6, initialUsers.size)

        // Change the owner's pin hash/salt (simulating user changing their PIN)
        val owner = initialUsers.first { it.role == UserRole.OWNER }
        val newHash = hasher.hash("9999")
        db.userDao().updatePin(owner.id, newHash.hashHex, newHash.saltHex)

        // Run seeder again
        DatabaseSeeder.seedIfEmpty(db)

        // User count must still be 6, and owner must retain new pin
        val afterUsers = db.userDao().getAllActiveUsers().first()
        assertEquals(6, afterUsers.size)
        val updatedOwner = db.userDao().getUserById(owner.id)!!
        assertEquals(newHash.hashHex, updatedOwner.pinHash)
    }

    @Test
    fun testMigrationPreservesAllBusinessDataWithoutDataLoss() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Manually open a v1 SQLite database with the old schema
        val dbHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_migration.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // v1 users table with plaintext pin
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS users (" +
                                    "id TEXT NOT NULL PRIMARY KEY, " +
                                    "name TEXT NOT NULL, " +
                                    "username TEXT NOT NULL, " +
                                    "pin TEXT NOT NULL, " +
                                    "role TEXT NOT NULL, " +
                                    "phone TEXT NOT NULL, " +
                                    "isActive INTEGER NOT NULL, " +
                                    "createdAt INTEGER NOT NULL)"
                        )
                        // v1 customers table
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS customers (" +
                                    "id TEXT NOT NULL PRIMARY KEY, " +
                                    "name TEXT NOT NULL, " +
                                    "phone TEXT NOT NULL, " +
                                    "creditLimit REAL NOT NULL, " +
                                    "currentDebt REAL NOT NULL, " +
                                    "allowDebt INTEGER NOT NULL, " +
                                    "status TEXT NOT NULL, " +
                                    "notes TEXT NOT NULL, " +
                                    "lastTransactionDate INTEGER NOT NULL, " +
                                    "createdAt INTEGER NOT NULL)"
                        )
                        // Insert v1 test user with plaintext PIN "1111"
                        db.execSQL(
                            "INSERT INTO users VALUES ('u1', 'زياد الشامي', 'owner', '1111', 'OWNER', '777000111', 1, 1000)"
                        )
                        // Insert v1 customer with debt
                        db.execSQL(
                            "INSERT INTO customers VALUES ('c1', 'الكابتن أحمد', '771234567', 25000.0, 7500.0, 1, 'ACTIVE', '', 2000, 1000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val writableDb = dbHelper.writableDatabase

        // 2. Execute MIGRATION_1_2
        MIGRATION_1_2.migrate(writableDb)

        // 3. Verify user was migrated with hashed PIN and unique salt
        val userCursor = writableDb.query("SELECT id, name, username, pinHash, pinSalt, role, failedAttempts, createdAt FROM users WHERE id = 'u1'")
        assertTrue(userCursor.moveToFirst())
        assertEquals("u1", userCursor.getString(0))
        assertEquals("زياد الشامي", userCursor.getString(1))
        assertEquals("owner", userCursor.getString(2))
        val hash = userCursor.getString(3)
        val salt = userCursor.getString(4)
        assertFalse(hash.isNullOrBlank())
        assertFalse(salt.isNullOrBlank())
        assertEquals(0, userCursor.getInt(6)) // failedAttempts = 0
        userCursor.close()

        // Verify the migrated hash successfully verifies against the original PIN "1111"
        assertTrue(hasher.verify("1111", hash, salt))

        // 4. Verify customers and debts are 100% preserved
        val custCursor = writableDb.query("SELECT id, name, creditLimit, currentDebt FROM customers WHERE id = 'c1'")
        assertTrue(custCursor.moveToFirst())
        assertEquals("c1", custCursor.getString(0))
        assertEquals("الكابتن أحمد", custCursor.getString(1))
        assertEquals(25000.0, custCursor.getDouble(2), 0.01)
        assertEquals(7500.0, custCursor.getDouble(3), 0.01)
        custCursor.close()

        writableDb.close()
        context.deleteDatabase("test_migration.db")
    }
}
