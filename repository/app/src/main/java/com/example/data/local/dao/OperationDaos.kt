package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id LIMIT 1")
    suspend fun getPurchaseById(id: String): PurchaseEntity?

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    fun getPurchaseItems(purchaseId: String): Flow<List<PurchaseItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)
}

@Dao
interface InventoryTransactionDao {
    @Query("SELECT * FROM inventory_transactions ORDER BY createdAt DESC LIMIT 200")
    fun getRecentTransactions(): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transactions WHERE rawMaterialId = :materialId ORDER BY createdAt DESC")
    fun getTransactionsForMaterial(materialId: String): Flow<List<InventoryTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: InventoryTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<InventoryTransactionEntity>)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE allowDebt = 1 AND status != 'BLOCKED' ORDER BY name ASC")
    fun getEligibleDebtCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE currentDebt > 0 ORDER BY currentDebt DESC")
    fun getIndebtedCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET currentDebt = :newDebt, status = :newStatus, lastTransactionDate = :now WHERE id = :id")
    suspend fun updateDebt(id: String, newDebt: Double, newStatus: com.example.domain.model.CustomerStatus, now: Long = System.currentTimeMillis())
}

@Dao
interface DebtTransactionDao {
    @Query("SELECT * FROM debt_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getTransactionsForCustomer(customerId: String): Flow<List<DebtTransactionEntity>>

    @Query("SELECT * FROM debt_transactions ORDER BY createdAt DESC LIMIT 100")
    fun getRecentDebtTransactions(): Flow<List<DebtTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtTransaction(transaction: DebtTransactionEntity)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' LIMIT 1")
    fun getCurrentOpenShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' LIMIT 1")
    suspend fun getCurrentOpenShiftSync(): ShiftEntity?

    @Query("SELECT * FROM shifts ORDER BY shiftNumber DESC")
    fun getAllShifts(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getShiftById(id: String): ShiftEntity?

    @Query("SELECT MAX(shiftNumber) FROM shifts")
    suspend fun getLastShiftNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: ShiftCashMovementEntity)

    @Query("SELECT * FROM shift_cash_movements WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun getCashMovements(shiftId: String): Flow<List<ShiftCashMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandover(handover: ShiftHandoverEntity)

    @Query("SELECT * FROM shift_handovers ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHandovers(): Flow<List<ShiftHandoverEntity>>

    @Query("SELECT * FROM shift_handovers ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastHandover(): ShiftHandoverEntity?
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun getSalesByShift(shiftId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE createdAt >= :startTime AND createdAt <= :endTime ORDER BY createdAt DESC")
    fun getSalesBetween(startTime: Long, endTime: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: String): SaleEntity?

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getSalesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItems(saleId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItemsSync(saleId: String): List<SaleItemEntity>

    @Query("UPDATE sales SET status = 'VOIDED', voidReason = :reason, voidedByUserId = :userId, voidedAt = :now WHERE id = :saleId")
    suspend fun voidSale(saleId: String, reason: String, userId: String, now: Long = System.currentTimeMillis())
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)
}

@Dao
interface WasteDao {
    @Query("SELECT * FROM waste_transactions ORDER BY createdAt DESC")
    fun getAllWaste(): Flow<List<WasteTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaste(waste: WasteTransactionEntity)
}

@Dao
interface StockAdjustmentDao {
    @Query("SELECT * FROM stock_counts ORDER BY date DESC")
    fun getAllStockCounts(): Flow<List<StockCountEntity>>

    @Query("SELECT * FROM stock_adjustments ORDER BY createdAt DESC")
    fun getAllStockAdjustments(): Flow<List<StockAdjustmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockCount(stockCount: StockCountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockAdjustment(adjustment: StockAdjustmentEntity)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM cafeteria_settings WHERE key = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: CafeteriaSettingEntity)
}
