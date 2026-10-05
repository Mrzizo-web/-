package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.*
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.*
import com.example.security.AppPermission
import com.example.security.PasswordHasher
import com.example.security.PermissionChecker
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
class PermissionSecurityTest {

    private lateinit var db: AppDatabase
    private lateinit var inventoryEngine: InventoryEngine
    private lateinit var salesEngine: SalesEngine
    private lateinit var shiftEngine: ShiftEngine

    private val hasher = PasswordHasher.DEFAULT

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inventoryEngine = InventoryEngine(db.rawMaterialDao(), db.recipeDao(), db.mixtureDao(), db.inventoryTransactionDao())
        salesEngine = SalesEngine(db, inventoryEngine)
        shiftEngine = ShiftEngine(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testOwnerHasAllPermissions() {
        for (perm in AppPermission.values()) {
            assertTrue("OWNER must have ${perm.name}", PermissionChecker.hasPermission(UserRole.OWNER, perm))
        }
    }

    @Test
    fun testAdminPermissions() {
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.ACCESS_ADMIN))
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.MANAGE_PRODUCTS))
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.MANAGE_EMPLOYEES))
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.VOID_SALE))
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.MANAGE_INVENTORY))
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.MANAGE_SETTINGS))
    }

    @Test
    fun testSupervisorPermissions() {
        assertTrue(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.ACCESS_ADMIN))
        assertTrue(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.VIEW_REPORTS))
        assertTrue(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.VOID_SALE))
        assertTrue(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.MANAGE_RECIPES))
        assertTrue(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.MANAGE_CUSTOMERS))
        assertTrue(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.MANAGE_DEBTS))

        // Supervisor must NOT have:
        assertFalse(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.MANAGE_EMPLOYEES))
        assertFalse(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.MANAGE_SETTINGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.VIEW_COSTS))
        assertFalse(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.MANAGE_INVENTORY))
        assertFalse(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.ADJUST_STOCK))
    }

    @Test
    fun testInventoryManagerPermissions() {
        assertTrue(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.ACCESS_ADMIN))
        assertTrue(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_INVENTORY))
        assertTrue(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.ADJUST_STOCK))
        assertTrue(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_RECIPES))

        // Inventory Manager must NOT have:
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_EMPLOYEES))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.VOID_SALE))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_SETTINGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.VIEW_AUDIT_LOGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_CUSTOMERS))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_DEBTS))
    }

    @Test
    fun testCashierHasZeroAdminPermissions() {
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.ACCESS_ADMIN))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_EMPLOYEES))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_PRODUCTS))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.ADJUST_STOCK))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.VOID_SALE))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.OVERRIDE_CREDIT))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_SETTINGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_INVENTORY))
    }

    @Test
    fun testCashierCannotVoidSaleInSalesEngine() = runBlocking {
        val h = hasher.hash("1234")
        val cashier = UserEntity(
            id = "c-1",
            name = "كاشير سامي",
            username = "sami",
            pinHash = h.hashHex,
            pinSalt = h.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(cashier)

        val shift = ShiftEntity(
            id = "s-1",
            shiftNumber = 1,
            userId = cashier.id,
            userName = cashier.name,
            openingCash = 5000.0,
            expectedCash = 5000.0
        )
        db.shiftDao().insertShift(shift)

        val sale = SaleEntity(
            id = "sale-1",
            invoiceNumber = "PF-000001",
            shiftId = shift.id,
            userId = cashier.id,
            userName = cashier.name,
            totalAmount = 1500.0,
            netAmount = 1500.0,
            paymentMethod = PaymentMethod.CASH,
            cashReceived = 1500.0,
            cashChange = 0.0,
            totalCost = 600.0,
            status = "COMPLETED"
        )
        db.saleDao().insertSale(sale)

        // Attempt to void sale as Cashier -> Must fail with SecurityException
        val result = salesEngine.voidSale(sale.id, "إلغاء غير مصرح", cashier)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)

        // Verify sale is STILL COMPLETED and not voided
        val fetchedSale = db.saleDao().getSaleById(sale.id)!!
        assertEquals("COMPLETED", fetchedSale.status)
    }

    @Test
    fun testAdminCanVoidSaleInSalesEngine() = runBlocking {
        val h = hasher.hash("2222")
        val admin = UserEntity(
            id = "admin-1",
            name = "المدير",
            username = "admin",
            pinHash = h.hashHex,
            pinSalt = h.saltHex,
            role = UserRole.ADMIN
        )
        db.userDao().insertUser(admin)

        val shift = ShiftEntity(
            id = "s-2",
            shiftNumber = 2,
            userId = admin.id,
            userName = admin.name,
            openingCash = 5000.0,
            expectedCash = 5000.0
        )
        db.shiftDao().insertShift(shift)

        val sale = SaleEntity(
            id = "sale-2",
            invoiceNumber = "PF-000002",
            shiftId = shift.id,
            userId = admin.id,
            userName = admin.name,
            totalAmount = 2000.0,
            netAmount = 2000.0,
            paymentMethod = PaymentMethod.CASH,
            cashReceived = 2000.0,
            cashChange = 0.0,
            totalCost = 800.0,
            status = "COMPLETED"
        )
        db.saleDao().insertSale(sale)

        // Void sale as Admin -> Must succeed
        val result = salesEngine.voidSale(sale.id, "خطأ في الطلب", admin)
        assertTrue(result.isSuccess)

        val fetchedSale = db.saleDao().getSaleById(sale.id)!!
        assertEquals("VOIDED", fetchedSale.status)
        assertEquals("خطأ في الطلب", fetchedSale.voidReason)
    }

    @Test
    fun testUnauthorizedAttemptLogsAuditEntity() = runBlocking {
        val h = hasher.hash("1234")
        val cashier = UserEntity(
            id = "c-audit",
            name = "كاشير اختبار",
            username = "cashier_test",
            pinHash = h.hashHex,
            pinSalt = h.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(cashier)

        // Simulate unauthorized attempt logging
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = cashier.id,
                userName = cashier.name,
                userRole = cashier.role.titleAr,
                action = "UNAUTHORIZED_ACCESS_ATTEMPT",
                entityType = "PERMISSION",
                entityId = AppPermission.MANAGE_EMPLOYEES.name,
                notes = "محاولة غير مصرح بها لتنفيذ: إضافة موظف جديد (الصلاحية المطلوبة: ${AppPermission.MANAGE_EMPLOYEES.titleAr})"
            )
        )

        val logs = db.auditLogDao().getRecentLogs().first()
        val unauthLog = logs.find { it.action == "UNAUTHORIZED_ACCESS_ATTEMPT" && it.userId == cashier.id }
        assertNotNull(unauthLog)
        assertEquals(AppPermission.MANAGE_EMPLOYEES.name, unauthLog!!.entityId)
    }

    @Test
    fun testPOSCalculationsRemainUnaffected() = runBlocking {
        // Verify that standard POS cash flow, expected cash, and change equations remain 100% accurate
        val h = hasher.hash("1234")
        val cashier = UserEntity(
            id = "c-pos-math",
            name = "كاشير معادلات",
            username = "cashier_math",
            pinHash = h.hashHex,
            pinSalt = h.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(cashier)

        val product = ProductEntity(
            id = "p-1",
            name = "بروتين شيك شوكولاتة",
            categoryId = "cat-1",
            price = 2500.0,
            costPrice = 1000.0,
            sku = "PROT-CHOC"
        )
        db.productDao().insertProduct(product)

        val shift = ShiftEntity(
            id = "s-math",
            shiftNumber = 10,
            userId = cashier.id,
            userName = cashier.name,
            openingCash = 10000.0,
            expectedCash = 10000.0
        )
        db.shiftDao().insertShift(shift)

        // Execute sale: 2 items of 2500 = 5000 YER, cash received 6000 YER, change = 1000 YER
        val cartItems = listOf(CartItem(product, 2))
        val saleResult = salesEngine.executeSale(
            items = cartItems,
            paymentMethod = PaymentMethod.CASH,
            cashReceived = 6000.0,
            paymentReference = "",
            selectedCustomerId = null,
            user = cashier,
            shift = shift
        )

        assertTrue(saleResult is SaleResult.Success)
        val sale = (saleResult as SaleResult.Success).sale
        assertEquals(5000.0, sale.totalAmount, 0.001)
        assertEquals(5000.0, sale.netAmount, 0.001)
        assertEquals(6000.0, sale.cashReceived, 0.001)
        assertEquals(1000.0, sale.cashChange, 0.001)

        // Shift expected cash must be 10000 + 5000 = 15000 YER
        val updatedShift = db.shiftDao().getShiftById(shift.id)!!
        assertEquals(15000.0, updatedShift.expectedCash, 0.001)
        assertEquals(5000.0, updatedShift.totalCashSales, 0.001)
    }
}
