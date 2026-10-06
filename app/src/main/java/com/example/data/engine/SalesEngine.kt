package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.CustomerStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.security.AppPermission
import com.example.security.PermissionChecker
import java.util.Locale
import java.util.UUID

data class CartItem(
    val product: ProductEntity,
    val quantity: Int
) {
    val totalPrice: Double
        get() = product.price * quantity
}

sealed class SaleResult {
    data class Success(val sale: SaleEntity, val invoiceNumber: String) : SaleResult()
    data class Error(val message: String) : SaleResult()
}

class SalesEngine(
    private val db: AppDatabase,
    private val inventoryEngine: InventoryEngine
) {
    private val saleDao = db.saleDao()
    private val shiftDao = db.shiftDao()
    private val customerDao = db.customerDao()
    private val debtTransactionDao = db.debtTransactionDao()
    private val auditLogDao = db.auditLogDao()

    suspend fun executeSale(
        shift: ShiftEntity,
        user: UserEntity,
        items: List<CartItem>,
        paymentMethod: PaymentMethod,
        cashReceived: Double = 0.0,
        paymentReference: String = "",
        selectedCustomerId: String? = null,
        discountAmount: Double = 0.0,
        notes: String = ""
    ): SaleResult {
        if (items.isEmpty()) {
            return SaleResult.Error("السلة فارغة، يرجى إضافة منتجات للطلب")
        }

        if (shift.status != ShiftStatus.OPEN) {
            return SaleResult.Error("لا يمكن إتمام البيع، الشفت مغلق أو غير نشط")
        }

        val totalAmount = items.sumOf { it.totalPrice }
        val netAmount = (totalAmount - discountAmount).coerceAtLeast(0.0)

        // Validation for Debt
        var customer: CustomerEntity? = null
        if (paymentMethod == PaymentMethod.DEBT) {
            if (selectedCustomerId.isNullOrEmpty()) {
                return SaleResult.Error("يرجى اختيار العميل لتسجيل الدين")
            }
            customer = customerDao.getCustomerById(selectedCustomerId)
            if (customer == null) {
                return SaleResult.Error("العميل غير موجود")
            }
            if (!customer.allowDebt || customer.status == CustomerStatus.BLOCKED) {
                return SaleResult.Error("العميل محظور أو غير مصرح له بالشراء بالدين")
            }
            if (customer.currentDebt + netAmount > customer.creditLimit) {
                if (!user.role.canOverrideCreditLimit) {
                    return SaleResult.Error(
                        "تم تجاوز الحد الائتماني للعميل (${customer.creditLimit.toLong()} ريال). الرصيد الحالي: ${customer.currentDebt.toLong()} ريال. يتطلب إذن المدير."
                    )
                }
            }
        }

        // Cash validation
        var cashChange = 0.0
        if (paymentMethod == PaymentMethod.CASH) {
            val received = if (cashReceived <= 0.0) netAmount else cashReceived
            if (received < netAmount) {
                return SaleResult.Error("المبلغ المستلم أقل من إجمالي الفاتورة")
            }
            cashChange = received - netAmount
        }

        return try {
            var completedSale: SaleEntity? = null
            var generatedInvoice = ""

            db.withTransaction {
                val count = saleDao.getSalesCount() + 1
                generatedInvoice = String.format(Locale.US, "PF-%06d", count)
                val saleId = UUID.randomUUID().toString()

                val saleEntity = SaleEntity(
                    id = saleId,
                    invoiceNumber = generatedInvoice,
                    shiftId = shift.id,
                    userId = user.id,
                    userName = user.name,
                    customerId = customer?.id,
                    customerName = customer?.name,
                    totalAmount = totalAmount,
                    discountAmount = discountAmount,
                    netAmount = netAmount,
                    paymentMethod = paymentMethod,
                    paymentReference = paymentReference,
                    cashReceived = if (paymentMethod == PaymentMethod.CASH) (if (cashReceived <= 0.0) netAmount else cashReceived) else 0.0,
                    cashChange = cashChange,
                    totalCost = items.sumOf { it.product.costPrice * it.quantity },
                    status = "COMPLETED",
                    notes = notes
                )

                // 1. Insert Sale
                saleDao.insertSale(saleEntity)

                // 2. Insert Sale Items
                val saleItems = items.map {
                    SaleItemEntity(
                        saleId = saleId,
                        productId = it.product.id,
                        productName = it.product.name,
                        quantity = it.quantity,
                        unitPrice = it.product.price,
                        totalPrice = it.totalPrice,
                        unitCost = it.product.costPrice,
                        totalCost = it.product.costPrice * it.quantity
                    )
                }
                saleDao.insertSaleItems(saleItems)

                // 3. Deduct Inventory for all items
                for (item in items) {
                    inventoryEngine.consumeForProduct(
                        productId = item.product.id,
                        quantity = item.quantity,
                        saleId = generatedInvoice,
                        userId = user.id,
                        userName = user.name
                    )
                }

                // 4. Update Shift Sales & Cash Drawer
                val currentShift = shiftDao.getShiftById(shift.id) ?: shift
                when (paymentMethod) {
                    PaymentMethod.CASH -> {
                        val newCashSales = currentShift.totalCashSales + netAmount
                        val newExpected = currentShift.openingCash + newCashSales - currentShift.totalExpensesCash
                        shiftDao.updateShift(
                            currentShift.copy(
                                totalCashSales = newCashSales,
                                expectedCash = newExpected
                            )
                        )
                        shiftDao.insertCashMovement(
                            ShiftCashMovementEntity(
                                shiftId = shift.id,
                                type = "SALE_CASH",
                                amount = netAmount,
                                referenceId = generatedInvoice,
                                notes = "مبيعات نقدية فاتورة $generatedInvoice"
                            )
                        )
                    }
                    PaymentMethod.E_WALLET -> {
                        shiftDao.updateShift(
                            currentShift.copy(
                                totalWalletSales = currentShift.totalWalletSales + netAmount
                            )
                        )
                    }
                    PaymentMethod.DEBT -> {
                        shiftDao.updateShift(
                            currentShift.copy(
                                totalDebtSales = currentShift.totalDebtSales + netAmount
                            )
                        )
                        // Update Customer Debt
                        customer?.let { cust ->
                            val newDebt = cust.currentDebt + netAmount
                            val newStatus = if (newDebt >= cust.creditLimit) CustomerStatus.CREDIT_LIMIT_REACHED else CustomerStatus.ACTIVE
                            customerDao.updateDebt(cust.id, newDebt, newStatus)

                            debtTransactionDao.insertDebtTransaction(
                                DebtTransactionEntity(
                                    customerId = cust.id,
                                    customerName = cust.name,
                                    type = "DEBT_SALE",
                                    amount = netAmount,
                                    paymentMethod = PaymentMethod.DEBT,
                                    referenceId = generatedInvoice,
                                    notes = "مشتريات بالدين فاتورة $generatedInvoice",
                                    userId = user.id,
                                    userName = user.name,
                                    balanceAfter = newDebt
                                )
                            )
                        }
                    }
                }

                // 5. Audit Log
                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = "SALE",
                        entityType = "SALE",
                        entityId = generatedInvoice,
                        previousValue = "",
                        newValue = "$netAmount YER - ${paymentMethod.titleAr}",
                        notes = "فاتورة $generatedInvoice للطلب بعدد ${items.size} أصناف"
                    )
                )

                completedSale = saleEntity
            }

            if (completedSale != null) {
                SaleResult.Success(completedSale, generatedInvoice)
            } else {
                SaleResult.Error("فشلت عملية البيع، لم يتم حفظ البيانات")
            }
        } catch (e: Exception) {
            SaleResult.Error("تعذر تسجيل العملية: ${e.localizedMessage ?: "خطأ غير متوقع"}. لم يتم خصم أي مبلغ ولم يتغير المخزون.")
        }
    }

    suspend fun voidSale(
        saleId: String,
        reason: String,
        user: UserEntity
    ): Result<SaleEntity> {
        if (!PermissionChecker.hasPermission(user, AppPermission.VOID_SALE)) {
            return Result.failure(SecurityException("المستخدم غير مصرح له بإلغاء واسترجاع الفواتير"))
        }

        val sale = saleDao.getSaleById(saleId) ?: return Result.failure(Exception("الفاتورة غير موجودة"))
        if (sale.status == "VOIDED") {
            return Result.failure(Exception("الفاتورة ملغاة بالفعل مسبقاً"))
        }

        return try {
            db.withTransaction {
                // 1. Mark sale as voided
                saleDao.voidSale(saleId, reason, user.id)

                // 2. Restore inventory for all items
                val saleItems = saleDao.getSaleItemsSync(saleId)
                for (item in saleItems) {
                    inventoryEngine.restoreForProduct(
                        productId = item.productId,
                        quantity = item.quantity,
                        saleId = sale.invoiceNumber,
                        userId = user.id,
                        userName = user.name
                    )
                }

                // 3. Reverse shift sales & cash drawer
                val currentShift = shiftDao.getShiftById(sale.shiftId)
                if (currentShift != null) {
                    when (sale.paymentMethod) {
                        PaymentMethod.CASH -> {
                            val newCashSales = (currentShift.totalCashSales - sale.netAmount).coerceAtLeast(0.0)
                            val newExpected = (currentShift.openingCash + newCashSales - currentShift.totalExpensesCash).coerceAtLeast(0.0)
                            shiftDao.updateShift(
                                currentShift.copy(
                                    totalCashSales = newCashSales,
                                    expectedCash = newExpected
                                )
                            )
                            shiftDao.insertCashMovement(
                                ShiftCashMovementEntity(
                                    shiftId = sale.shiftId,
                                    type = "VOID_REFUND",
                                    amount = -sale.netAmount,
                                    referenceId = sale.invoiceNumber,
                                    notes = "استرجاع نقدي بسبب إلغاء فاتورة ${sale.invoiceNumber}"
                                )
                            )
                        }
                        PaymentMethod.E_WALLET -> {
                            val newWalletSales = (currentShift.totalWalletSales - sale.netAmount).coerceAtLeast(0.0)
                            shiftDao.updateShift(currentShift.copy(totalWalletSales = newWalletSales))
                        }
                        PaymentMethod.DEBT -> {
                            val newDebtSales = (currentShift.totalDebtSales - sale.netAmount).coerceAtLeast(0.0)
                            shiftDao.updateShift(currentShift.copy(totalDebtSales = newDebtSales))

                            // Reverse customer debt
                            sale.customerId?.let { custId ->
                                val customer = customerDao.getCustomerById(custId)
                                if (customer != null) {
                                    val newDebt = (customer.currentDebt - sale.netAmount).coerceAtLeast(0.0)
                                    customerDao.updateDebt(custId, newDebt, CustomerStatus.ACTIVE)
                                    debtTransactionDao.insertDebtTransaction(
                                        DebtTransactionEntity(
                                            customerId = custId,
                                            customerName = customer.name,
                                            type = "DEBT_VOID",
                                            amount = -sale.netAmount,
                                            paymentMethod = PaymentMethod.DEBT,
                                            referenceId = sale.invoiceNumber,
                                            notes = "إلغاء مديونية بسبب إلغاء الفاتورة ${sale.invoiceNumber}",
                                            userId = user.id,
                                            userName = user.name,
                                            balanceAfter = newDebt
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Audit Log
                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = "SALE_VOIDED",
                        entityType = "SALE",
                        entityId = sale.invoiceNumber,
                        previousValue = "${sale.netAmount} YER (${sale.paymentMethod.titleAr})",
                        newValue = "VOIDED",
                        notes = "إلغاء الفاتورة ${sale.invoiceNumber} بسبب: $reason"
                    )
                )
            }
            Result.success(sale.copy(status = "VOIDED", voidReason = reason))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
