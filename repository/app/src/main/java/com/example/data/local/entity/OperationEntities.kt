package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.CustomerStatus
import com.example.domain.model.InventoryTxType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.WasteReason
import java.util.UUID

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val supplierId: String,
    val supplierName: String,
    val invoiceNumber: String,
    val purchaseDate: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val notes: String = "",
    val createdByUserId: String,
    val createdByUserName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val purchaseId: String,
    val rawMaterialId: String,
    val rawMaterialName: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double
)

@Entity(tableName = "inventory_transactions")
data class InventoryTransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val rawMaterialId: String,
    val rawMaterialName: String,
    val type: InventoryTxType,
    val quantityChange: Double, // positive for purchase/return, negative for sale/waste
    val unit: String,
    val previousQuantity: Double,
    val newQuantity: Double,
    val referenceId: String = "", // SaleId, PurchaseId, WasteId, AdjustmentId
    val notes: String = "",
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val creditLimit: Double = 15000.0, // Limit in YER
    val currentDebt: Double = 0.0,
    val allowDebt: Boolean = true,
    val status: CustomerStatus = CustomerStatus.ACTIVE,
    val notes: String = "",
    val lastTransactionDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "debt_transactions")
data class DebtTransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val customerName: String,
    val type: String, // DEBT_SALE or DEBT_PAYMENT
    val amount: Double,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val referenceId: String = "",
    val notes: String = "",
    val userId: String,
    val userName: String,
    val balanceAfter: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val shiftNumber: Int,
    val userId: String,
    val userName: String,
    val status: ShiftStatus = ShiftStatus.OPEN,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val openingCash: Double = 0.0, // Entered at shift start
    val totalCashSales: Double = 0.0,
    val totalWalletSales: Double = 0.0,
    val totalDebtSales: Double = 0.0,
    val totalExpensesCash: Double = 0.0,
    val expectedCash: Double = 0.0, // openingCash + totalCashSales - totalExpensesCash
    val actualCash: Double = 0.0, // Counted at close
    val handedOverCash: Double = 0.0, // Handed to supervisor
    val leftForNextShiftCash: Double = 0.0, // Left in drawer for next cashier
    val discrepancyAmount: Double = 0.0, // actualCash - expectedCash
    val notes: String = ""
)

@Entity(tableName = "shift_cash_movements")
data class ShiftCashMovementEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val shiftId: String,
    val type: String, // OPENING, SALE_CASH, EXPENSE_PAYOUT, CASH_DROP, CLOSING
    val amount: Double,
    val referenceId: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shift_handovers")
data class ShiftHandoverEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val fromShiftId: String,
    val toShiftId: String,
    val fromUserId: String,
    val fromUserName: String,
    val toUserId: String,
    val toUserName: String,
    val expectedLeftAmount: Double,
    val actualReceivedAmount: Double,
    val discrepancy: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String, // e.g. PF-000124
    val shiftId: String,
    val userId: String,
    val userName: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val totalAmount: Double,
    val discountAmount: Double = 0.0,
    val netAmount: Double,
    val paymentMethod: PaymentMethod,
    val paymentReference: String = "", // e.g. wallet transaction ref
    val cashReceived: Double = 0.0,
    val cashChange: Double = 0.0,
    val totalCost: Double = 0.0,
    val status: String = "COMPLETED", // COMPLETED, VOIDED
    val voidReason: String? = null,
    val voidedByUserId: String? = null,
    val voidedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
)

@Entity(tableName = "sale_items")
data class SaleItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val saleId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val shiftId: String? = null,
    val title: String,
    val category: String, // صيانة, أدوات نظافة, تغليف وأكياس, ضيافة, أخرى
    val amount: Double,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val paidTo: String = "",
    val notes: String = "",
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "waste_transactions")
data class WasteTransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val rawMaterialId: String,
    val rawMaterialName: String,
    val quantity: Double,
    val unit: String,
    val reason: WasteReason,
    val estimatedCost: Double,
    val notes: String = "",
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_counts")
data class StockCountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val countedByUserId: String,
    val countedByUserName: String,
    val notes: String = "",
    val isApplied: Boolean = true
)

@Entity(tableName = "stock_adjustments")
data class StockAdjustmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val stockCountId: String,
    val rawMaterialId: String,
    val rawMaterialName: String,
    val systemStock: Double,
    val actualStock: Double,
    val discrepancy: Double, // actualStock - systemStock
    val unit: String,
    val reason: String = "",
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val userRole: String,
    val action: String, // LOGIN, SALE, VOID, PRICE_CHANGE, SHIFT_OPEN, SHIFT_CLOSE, etc.
    val entityType: String, // SALE, SHIFT, PRODUCT, INVENTORY, DEBT, USER
    val entityId: String = "",
    val previousValue: String = "",
    val newValue: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cafeteria_settings")
data class CafeteriaSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
