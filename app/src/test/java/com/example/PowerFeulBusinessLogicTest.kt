package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.*
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.seed.DatabaseSeeder
import com.example.domain.model.*
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
class PowerFeulBusinessLogicTest {

    private lateinit var db: AppDatabase
    private lateinit var shiftEngine: ShiftEngine
    private lateinit var inventoryEngine: InventoryEngine
    private lateinit var salesEngine: SalesEngine

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shiftEngine = ShiftEngine(db)
        inventoryEngine = InventoryEngine(
            rawMaterialDao = db.rawMaterialDao(),
            recipeDao = db.recipeDao(),
            mixtureDao = db.mixtureDao(),
            inventoryTransactionDao = db.inventoryTransactionDao()
        )
        salesEngine = SalesEngine(db, inventoryEngine)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testUnitConverter() {
        // Grams to KG
        val gToKg = UnitConverter.convert(500.0, "G", "KG")
        assertEquals(0.5, gToKg, 0.001)

        // KG to Grams
        val kgToG = UnitConverter.convert(1.5, "KG", "G")
        assertEquals(1500.0, kgToG, 0.001)

        // ML to Liter
        val mlToL = UnitConverter.convert(250.0, "ML", "LITER")
        assertEquals(0.25, mlToL, 0.001)

        // Liter to ML
        val lToMl = UnitConverter.convert(2.0, "LITER", "ML")
        assertEquals(2000.0, lToMl, 0.001)

        // Direct same unit
        val same = UnitConverter.convert(10.0, "PIECE", "PIECE")
        assertEquals(10.0, same, 0.001)
    }

    @Test
    fun testUserRolePermissions() {
        // CASHIER
        assertFalse(UserRole.CASHIER.canAccessAdmin)
        assertFalse(UserRole.CASHIER.canManageInventory)
        assertFalse(UserRole.CASHIER.canViewReports)
        assertFalse(UserRole.CASHIER.canManageEmployees)

        // INVENTORY_MANAGER
        assertTrue(UserRole.INVENTORY_MANAGER.canAccessAdmin)
        assertTrue(UserRole.INVENTORY_MANAGER.canManageInventory)
        assertFalse(UserRole.INVENTORY_MANAGER.canManageEmployees)

        // ADMIN & OWNER
        assertTrue(UserRole.ADMIN.canAccessAdmin)
        assertTrue(UserRole.ADMIN.canManageInventory)
        assertTrue(UserRole.ADMIN.canViewReports)
        assertTrue(UserRole.ADMIN.canManageEmployees)

        assertTrue(UserRole.OWNER.canAccessAdmin)
        assertTrue(UserRole.OWNER.canManageInventory)
        assertTrue(UserRole.OWNER.canViewReports)
        assertTrue(UserRole.OWNER.canManageEmployees)
    }

    @Test
    fun testDatabaseSeeder() = runBlocking {
        DatabaseSeeder.seedIfEmpty(db)

        val users = db.userDao().getAllActiveUsers().first()
        assertTrue(users.isNotEmpty())
        assertTrue(users.any { it.role == UserRole.OWNER })
        assertTrue(users.any { it.role == UserRole.CASHIER })

        val categories = db.categoryDao().getActiveCategories().first()
        assertTrue(categories.isNotEmpty())

        val products = db.productDao().getActiveProducts().first()
        assertTrue(products.isNotEmpty())

        val materials = db.rawMaterialDao().getAllRawMaterials().first()
        assertTrue(materials.isNotEmpty())

        val customers = db.customerDao().getAllCustomers().first()
        assertTrue(customers.isNotEmpty())
    }

    @Test
    fun testShiftLifecycle() = runBlocking {
        val hash = PasswordHasher.DEFAULT.hash("1234")
        val testUser = UserEntity(
            id = "cashier-1",
            name = "محمد سليم",
            username = "mohamed",
            pinHash = hash.hashHex,
            pinSalt = hash.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(testUser)

        // 1. Start Shift
        val startResult = shiftEngine.startShift(
            user = testUser,
            openingCash = 10000.0,
            notes = "بداية الوردية الصباحية"
        )
        assertTrue(startResult is ShiftResult.Success)
        val shift = (startResult as ShiftResult.Success).shift
        assertEquals(10000.0, shift.openingCash, 0.01)
        assertEquals(10000.0, shift.expectedCash, 0.01)
        assertEquals(ShiftStatus.OPEN, shift.status)

        // 2. Add Cash Sale
        val openShift = db.shiftDao().getCurrentOpenShiftSync()
        assertNotNull(openShift)

        // Record a cash sale of 2500 YER
        db.shiftDao().updateShift(
            openShift!!.copy(
                totalCashSales = openShift.totalCashSales + 2500.0,
                expectedCash = openShift.expectedCash + 2500.0
            )
        )

        val updatedShift = db.shiftDao().getCurrentOpenShiftSync()!!
        assertEquals(12500.0, updatedShift.expectedCash, 0.01)

        // 3. Record an expense of 500 YER
        db.shiftDao().updateShift(
            updatedShift.copy(
                totalExpensesCash = updatedShift.totalExpensesCash + 500.0,
                expectedCash = updatedShift.expectedCash - 500.0
            )
        )
        val afterExpenseShift = db.shiftDao().getCurrentOpenShiftSync()!!
        assertEquals(12000.0, afterExpenseShift.expectedCash, 0.01)

        // 4. Close Shift with discrepancy
        // Expected is 12000 YER. Actual in drawer is 11900 YER (shortage of -100).
        // Allocated: 10000 to supervisor + 1900 left for next = 11900 (matches actual).
        val closeResult = shiftEngine.closeShift(
            shiftId = afterExpenseShift.id,
            user = testUser,
            actualCash = 11900.0,
            handedOverCash = 10000.0,
            leftForNextShiftCash = 1900.0,
            notes = "عجز بسيط 100 ريال"
        )
        assertTrue(closeResult is ShiftResult.Success)
        val closedShift = (closeResult as ShiftResult.Success).shift
        assertEquals(ShiftStatus.DISCREPANCY, closedShift.status)
        assertEquals(-100.0, closedShift.discrepancyAmount, 0.01)
        assertEquals(1900.0, closedShift.leftForNextShiftCash, 0.01)

        // 5. Next Cashier Accepts Handover
        val hash2 = PasswordHasher.DEFAULT.hash("5678")
        val nextUser = UserEntity(
            id = "cashier-2",
            name = "عمر خالد",
            username = "omar",
            pinHash = hash2.hashHex,
            pinSalt = hash2.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(nextUser)

        val handoverResult = shiftEngine.acceptHandover(
            fromShiftId = closedShift.id,
            toUser = nextUser,
            actualReceivedCash = 1900.0,
            notes = "استلام كامل ومطابق"
        )
        assertTrue(handoverResult is ShiftResult.Success)
        val newShift = (handoverResult as ShiftResult.Success).shift
        assertEquals(1900.0, newShift.openingCash, 0.01)
        assertEquals(ShiftStatus.OPEN, newShift.status)
    }

    @Test
    fun testSalesEngineAndInventoryDeduction() = runBlocking {
        val hash = PasswordHasher.DEFAULT.hash("1234")
        val testUser = UserEntity(
            id = "user-1",
            name = "محمد",
            username = "mohamed",
            pinHash = hash.hashHex,
            pinSalt = hash.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(testUser)

        // Setup raw material with initial stock 10.0 KG
        val banana = RawMaterialEntity(
            id = "mat-banana",
            name = "موز طازج",
            sku = "RM-BANANA",
            baseUnit = "KG",
            currentStock = 10.0,
            minStock = 2.0,
            lastPurchasePrice = 1200.0,
            avgCostPerUnit = 1200.0
        )
        db.rawMaterialDao().insertRawMaterial(banana)

        // Setup product linked to recipe
        val smoothie = ProductEntity(
            id = "prod-smoothie",
            categoryId = "cat-smoothies",
            name = "سموذي الموز المنعش",
            price = 1500.0,
            isAvailable = true
        )
        db.productDao().insertProduct(smoothie)

        // Recipe: 200 Grams of Banana per smoothie
        val recipe = RecipeEntity(
            id = "rec-smoothie",
            productId = smoothie.id,
            name = "وصفة سموذي الموز",
            notes = "خلط الموز الطازج"
        )
        db.recipeDao().insertRecipe(recipe)

        val recipeItem = RecipeItemEntity(
            recipeId = recipe.id,
            rawMaterialId = banana.id,
            name = "موز طازج",
            quantity = 200.0,
            unit = "G"
        )
        db.recipeDao().insertRecipeItems(listOf(recipeItem))

        // Start shift
        val shift = (shiftEngine.startShift(
            testUser, 5000.0, "وردية"
        ) as ShiftResult.Success).shift

        // Perform Cash Sale for 2 smoothies (requires 400g = 0.4kg of banana)
        val cartItems = listOf(
            CartItem(smoothie, quantity = 2)
        )

        val saleResult = salesEngine.executeSale(
            shift = shift,
            user = testUser,
            items = cartItems,
            paymentMethod = PaymentMethod.CASH,
            cashReceived = 3000.0,
            selectedCustomerId = null
        )

        assertTrue(saleResult is SaleResult.Success)
        val sale = (saleResult as SaleResult.Success).sale
        assertEquals(3000.0, sale.netAmount, 0.01)
        assertEquals(PaymentMethod.CASH, sale.paymentMethod)

        // Verify Inventory was deducted: 10.0 - 0.4 = 9.6 KG
        val updatedBanana = db.rawMaterialDao().getRawMaterialById("mat-banana")!!
        assertEquals(9.6, updatedBanana.currentStock, 0.001)

        // Test Voiding Sale: Restores Inventory back to 10.0 KG (authorized by supervisor)
        val supervisor = UserEntity(
            id = "sup-1",
            name = "المشرف",
            username = "supervisor",
            pinHash = hash.hashHex,
            pinSalt = hash.saltHex,
            role = UserRole.SUPERVISOR
        )
        db.userDao().insertUser(supervisor)

        val voidResult = salesEngine.voidSale(
            saleId = sale.id,
            reason = "طلب العميل إلغاء الفاتورة",
            user = supervisor
        )
        assertTrue(voidResult.isSuccess)
        val restoredBanana = db.rawMaterialDao().getRawMaterialById("mat-banana")!!
        assertEquals(10.0, restoredBanana.currentStock, 0.001)
    }

    @Test
    fun testDebtSaleAndCreditLimitEnforcement() = runBlocking {
        val hash = PasswordHasher.DEFAULT.hash("1234")
        val testUser = UserEntity(
            id = "user-1",
            name = "محمد",
            username = "mohamed",
            pinHash = hash.hashHex,
            pinSalt = hash.saltHex,
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(testUser)

        // Customer with credit limit 5000 YER and current debt 0
        val customer = CustomerEntity(
            id = "cust-1",
            name = "الكابتن أحمد الزبيري",
            phone = "771000222",
            creditLimit = 5000.0,
            currentDebt = 0.0,
            allowDebt = true,
            status = CustomerStatus.ACTIVE
        )
        db.customerDao().insertCustomer(customer)

        val proteinBar = ProductEntity(
            id = "prod-bar",
            categoryId = "cat-supps",
            name = "بروتين بار",
            price = 3000.0,
            isAvailable = true
        )
        db.productDao().insertProduct(proteinBar)

        val shift = (shiftEngine.startShift(
            testUser, 5000.0, "وردية"
        ) as ShiftResult.Success).shift

        // 1. First Debt Sale: 3000 YER (Within Limit)
        val firstSale = salesEngine.executeSale(
            shift = shift,
            user = testUser,
            items = listOf(CartItem(proteinBar, 1)),
            paymentMethod = PaymentMethod.DEBT,
            selectedCustomerId = customer.id
        )
        assertTrue(firstSale is SaleResult.Success)

        val custAfterFirst = db.customerDao().getCustomerById("cust-1")!!
        assertEquals(3000.0, custAfterFirst.currentDebt, 0.01)

        // 2. Second Debt Sale: 3000 YER (3000 + 3000 = 6000 > 5000 Limit -> Must Fail!)
        val secondSale = salesEngine.executeSale(
            shift = shift,
            user = testUser,
            items = listOf(CartItem(proteinBar, 1)),
            paymentMethod = PaymentMethod.DEBT,
            selectedCustomerId = customer.id
        )
        assertTrue(secondSale is SaleResult.Error)
        assertTrue((secondSale as SaleResult.Error).message.contains("الحد الائتماني"))

        // Debt remains 3000
        val custAfterSecond = db.customerDao().getCustomerById("cust-1")!!
        assertEquals(3000.0, custAfterSecond.currentDebt, 0.01)

        // 3. Customer pays 2000 YER debt
        val cust = db.customerDao().getCustomerById(customer.id)!!
        val newDebt = (cust.currentDebt - 2000.0).coerceAtLeast(0.0)
        db.customerDao().updateDebt(customer.id, newDebt, CustomerStatus.ACTIVE)
        db.debtTransactionDao().insertDebtTransaction(
            DebtTransactionEntity(
                customerId = customer.id,
                customerName = cust.name,
                type = "DEBT_PAYMENT",
                amount = 2000.0,
                paymentMethod = PaymentMethod.CASH,
                notes = "سداد دفعة نقدية",
                userId = testUser.id,
                userName = testUser.name,
                balanceAfter = newDebt
            )
        )

        // Debt is now 3000 - 2000 = 1000
        val custAfterPay = db.customerDao().getCustomerById("cust-1")!!
        assertEquals(1000.0, custAfterPay.currentDebt, 0.01)
    }
}
