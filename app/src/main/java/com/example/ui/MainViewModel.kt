package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.*
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.seed.DatabaseSeeder
import com.example.domain.model.CustomerStatus
import com.example.domain.model.InventoryTxType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import com.example.domain.model.WasteReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    POS,
    ADMIN
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val db = AppDatabase.getInstance(application)

    private val costEngine = CostEngine(db.rawMaterialDao(), db.mixtureDao(), db.recipeDao())
    private val inventoryEngine = InventoryEngine(db.rawMaterialDao(), db.recipeDao(), db.mixtureDao(), db.inventoryTransactionDao())
    private val salesEngine = SalesEngine(db, inventoryEngine)
    private val shiftEngine = ShiftEngine(db)
    private val purchaseEngine = PurchaseEngine(db, inventoryEngine, costEngine)
    private val powerAiEngine = PowerAiEngine(db)

    // Current Session State
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _activeScreen = MutableStateFlow(AppScreen.POS)
    val activeScreen = _activeScreen.asStateFlow()

    // Dialog & UI Feedback States
    val showStartShiftDialog = MutableStateFlow(false)
    val showCloseShiftDialog = MutableStateFlow(false)
    val showHandoverDialog = MutableStateFlow(false)
    val showPaymentDialog = MutableStateFlow(false)
    val lastCompletedSale = MutableStateFlow<SaleEntity?>(null)
    val snackbarMessage = MutableStateFlow<String?>(null)
    val loginErrorMessage = MutableStateFlow<String?>(null)

    // Cart
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems = _cartItems.asStateFlow()

    // Data Flows from Room
    val users = db.userDao().getAllActiveUsers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allUsers = db.userDao().getAllUsers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = db.categoryDao().getActiveCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val products = db.productDao().getActiveProducts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rawMaterials = db.rawMaterialDao().getAllRawMaterials().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recipes = db.recipeDao().getAllRecipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mixtures = db.mixtureDao().getAllMixtures().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val suppliers = db.supplierDao().getAllSuppliers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val purchases = db.purchaseDao().getAllPurchases().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customers = db.customerDao().getAllCustomers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val eligibleDebtCustomers = db.customerDao().getEligibleDebtCustomers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentShift = db.shiftDao().getCurrentOpenShift().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val allShifts = db.shiftDao().getAllShifts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sales = db.saleDao().getAllSales().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val expenses = db.expenseDao().getAllExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditLogs = db.auditLogDao().getRecentLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseSeeder.seedIfEmpty(db)
        }
    }

    fun login(user: UserEntity) {
        _currentUser.value = user
        loginErrorMessage.value = null
        _activeScreen.value = AppScreen.POS
        _cartItems.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            // Check shift state
            val open = db.shiftDao().getCurrentOpenShiftSync()
            if (open == null) {
                val lastShift = db.shiftDao().getAllShifts().firstOrNull()?.firstOrNull()
                if (lastShift != null && lastShift.status == ShiftStatus.CLOSED && lastShift.leftForNextShiftCash > 0) {
                    showHandoverDialog.value = true
                } else {
                    showStartShiftDialog.value = true
                }
            }

            // Log Login in Audit
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "LOGIN",
                    entityType = "USER",
                    entityId = user.username,
                    notes = "تسجيل دخول ناجح إلى النظام"
                )
            )
        }
    }

    fun logout() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch(Dispatchers.IO) {
                db.auditLogDao().insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = "LOGOUT",
                        entityType = "USER",
                        entityId = user.username,
                        notes = "تسجيل خروج من النظام"
                    )
                )
            }
        }
        _currentUser.value = null
        _cartItems.value = emptyList()
        _activeScreen.value = AppScreen.POS
    }

    fun navigateTo(screen: AppScreen) {
        _activeScreen.value = screen
    }

    // Cart Operations
    fun addToCart(product: ProductEntity) {
        if (!product.isAvailable) {
            snackbarMessage.value = "المنتج (${product.name}) غير متوفر حالياً"
            return
        }
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val item = current[index]
            current[index] = item.copy(quantity = item.quantity + 1)
        } else {
            current.add(CartItem(product = product, quantity = 1))
        }
        _cartItems.value = current
    }

    fun removeFromCart(product: ProductEntity) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val item = current[index]
            if (item.quantity > 1) {
                current[index] = item.copy(quantity = item.quantity - 1)
            } else {
                current.removeAt(index)
            }
            _cartItems.value = current
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    // Shift Operations
    fun startShift(openingCash: Double, notes: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = shiftEngine.startShift(user, openingCash, notes)
            when (result) {
                is ShiftResult.Success -> {
                    showStartShiftDialog.value = false
                    snackbarMessage.value = "تم بدء الشفت #${result.shift.shiftNumber} بنجاح"
                }
                is ShiftResult.Error -> {
                    snackbarMessage.value = result.message
                }
            }
        }
    }

    fun closeShift(actualCash: Double, handedOverCash: Double, leftForNextShiftCash: Double, notes: String) {
        val user = _currentUser.value ?: return
        val shift = currentShift.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = shiftEngine.closeShift(shift.id, user, actualCash, handedOverCash, leftForNextShiftCash, notes)
            when (result) {
                is ShiftResult.Success -> {
                    showCloseShiftDialog.value = false
                    snackbarMessage.value = "تم إغلاق الشفت #${result.shift.shiftNumber} بنجاح"
                }
                is ShiftResult.Error -> {
                    snackbarMessage.value = result.message
                }
            }
        }
    }

    fun acceptHandover(actualReceivedCash: Double, notes: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val lastShift = db.shiftDao().getAllShifts().firstOrNull()?.firstOrNull()
            if (lastShift != null) {
                val result = shiftEngine.acceptHandover(lastShift.id, user, actualReceivedCash, notes)
                when (result) {
                    is ShiftResult.Success -> {
                        showHandoverDialog.value = false
                        snackbarMessage.value = "تم استلام الشفت #${result.shift.shiftNumber} بنجاح"
                    }
                    is ShiftResult.Error -> {
                        snackbarMessage.value = result.message
                    }
                }
            }
        }
    }

    // Sale Operations
    fun executeSale(
        paymentMethod: PaymentMethod,
        cashReceived: Double,
        reference: String,
        customerId: String?
    ) {
        val user = _currentUser.value ?: return
        val shift = currentShift.value
        if (shift == null || shift.status != ShiftStatus.OPEN) {
            snackbarMessage.value = "لا يمكن البيع بدون شفت مفتوح"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = salesEngine.executeSale(
                shift = shift,
                user = user,
                items = _cartItems.value,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                paymentReference = reference,
                selectedCustomerId = customerId
            )
            when (result) {
                is SaleResult.Success -> {
                    showPaymentDialog.value = false
                    _cartItems.value = emptyList()
                    lastCompletedSale.value = result.sale
                    snackbarMessage.value = "تمت عملية البيع بنجاح (فاتورة ${result.invoiceNumber})"
                }
                is SaleResult.Error -> {
                    snackbarMessage.value = result.message
                }
            }
        }
    }

    // Admin Operations
    fun addProduct(name: String, catId: String, price: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val prod = ProductEntity(
                name = name,
                categoryId = catId,
                price = price,
                costPrice = price * 0.4,
                sku = "PF-${(System.currentTimeMillis() % 1000)}"
            )
            db.productDao().insertProduct(prod)
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "ADD_PRODUCT",
                    entityType = "PRODUCT",
                    entityId = name,
                    newValue = "$price YER",
                    notes = "إضافة منتج جديد: $name بسعر $price ريال"
                )
            )
            snackbarMessage.value = "تم إضافة المنتج بنجاح"
        }
    }

    fun updateProductPrice(productId: String, newPrice: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val prod = db.productDao().getProductById(productId) ?: return@launch
            val oldPrice = prod.price
            db.productDao().updateProduct(prod.copy(price = newPrice))
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "PRICE_CHANGE",
                    entityType = "PRODUCT",
                    entityId = prod.name,
                    previousValue = "$oldPrice YER",
                    newValue = "$newPrice YER",
                    notes = "تعديل سعر المنتج ${prod.name} من $oldPrice إلى $newPrice ريال"
                )
            )
            snackbarMessage.value = "تم تحديث سعر المنتج"
        }
    }

    fun toggleProductAvailable(productId: String, isAvailable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.productDao().updateAvailability(productId, isAvailable)
        }
    }

    fun addRawMaterial(name: String, sku: String, baseUnit: String, minStock: Double, price: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val material = RawMaterialEntity(
                name = name,
                sku = sku.ifEmpty { "RM-${System.currentTimeMillis() % 1000}" },
                baseUnit = baseUnit,
                currentStock = 0.0,
                minStock = minStock,
                lastPurchasePrice = price,
                avgCostPerUnit = price
            )
            db.rawMaterialDao().insertRawMaterial(material)
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "ADD_MATERIAL",
                    entityType = "INVENTORY",
                    entityId = name,
                    newValue = "$price YER / $baseUnit",
                    notes = "إضافة مادة خام جديدة: $name"
                )
            )
            snackbarMessage.value = "تم إضافة المادة الخام بنجاح"
        }
    }

    fun addCustomer(name: String, phone: String, creditLimit: Double, allowDebt: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val cust = CustomerEntity(
                name = name,
                phone = phone,
                creditLimit = creditLimit,
                currentDebt = 0.0,
                allowDebt = allowDebt,
                status = CustomerStatus.ACTIVE
            )
            db.customerDao().insertCustomer(cust)
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "ADD_CUSTOMER",
                    entityType = "CUSTOMER",
                    entityId = name,
                    newValue = "حد ائتماني: $creditLimit YER",
                    notes = "إضافة عميل جديد: $name"
                )
            )
            snackbarMessage.value = "تم تسجيل العميل بنجاح"
        }
    }

    fun addEmployee(name: String, username: String, pin: String, role: UserRole, phone: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.userDao().getUserByPin(pin)
            if (existing != null) {
                snackbarMessage.value = "رمز PIN مستخدم بالفعل لموظف آخر"
                return@launch
            }
            val newUser = UserEntity(
                name = name,
                username = username,
                pin = pin,
                role = role,
                phone = phone
            )
            db.userDao().insertUser(newUser)
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "ADD_USER",
                    entityType = "USER",
                    entityId = username,
                    newValue = role.titleAr,
                    notes = "إضافة موظف جديد: $name بدرو ${role.titleAr}"
                )
            )
            snackbarMessage.value = "تم إضافة الموظف بنجاح"
        }
    }

    fun toggleUserActive(userId: String, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.userDao().setUserActiveStatus(userId, isActive)
            snackbarMessage.value = if (isActive) "تم تفعيل حساب الموظف" else "تم تعطيل حساب الموظف"
        }
    }

    fun addExpense(title: String, category: String, amount: Double, isCash: Boolean, paidTo: String, notes: String) {
        val user = _currentUser.value ?: return
        val shift = currentShift.value
        viewModelScope.launch(Dispatchers.IO) {
            val expense = ExpenseEntity(
                shiftId = shift?.id,
                title = title,
                category = category,
                amount = amount,
                paymentMethod = if (isCash) PaymentMethod.CASH else PaymentMethod.E_WALLET,
                paidTo = paidTo,
                notes = notes,
                userId = user.id,
                userName = user.name
            )
            db.expenseDao().insertExpense(expense)

            // If cash and shift open, decrease shift expected cash
            if (isCash && shift != null && shift.status == ShiftStatus.OPEN) {
                val newExpensesCash = shift.totalExpensesCash + amount
                val newExpected = (shift.openingCash + shift.totalCashSales - newExpensesCash).coerceAtLeast(0.0)
                db.shiftDao().updateShift(
                    shift.copy(
                        totalExpensesCash = newExpensesCash,
                        expectedCash = newExpected
                    )
                )
                db.shiftDao().insertCashMovement(
                    ShiftCashMovementEntity(
                        shiftId = shift.id,
                        type = "EXPENSE_PAYOUT",
                        amount = -amount,
                        referenceId = title,
                        notes = "صرف مصروفات ($title) من الدرج: $amount ريال"
                    )
                )
            }

            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "EXPENSE",
                    entityType = "EXPENSE",
                    entityId = title,
                    newValue = "$amount YER",
                    notes = "تسجيل مصروف: $title بمبلغ $amount ريال ($category)"
                )
            )
            snackbarMessage.value = "تم تسجيل المصروف بنجاح"
        }
    }

    fun createPurchase(supplierId: String, supplierName: String, invoiceNo: String, materialId: String, qty: Double, unit: String, unitPrice: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            purchaseEngine.createPurchase(
                user = user,
                supplierId = supplierId,
                supplierName = supplierName,
                invoiceNumber = invoiceNo,
                items = listOf(NewPurchaseItem(materialId, qty, unit, unitPrice))
            )
            snackbarMessage.value = "تم تسجيل التوريد وزيادة المخزون بنجاح"
        }
    }

    fun recordDebtPayment(customerId: String, amount: Double, isCash: Boolean, notes: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val cust = db.customerDao().getCustomerById(customerId) ?: return@launch
            val newDebt = (cust.currentDebt - amount).coerceAtLeast(0.0)
            db.customerDao().updateDebt(customerId, newDebt, CustomerStatus.ACTIVE)
            db.debtTransactionDao().insertDebtTransaction(
                DebtTransactionEntity(
                    customerId = customerId,
                    customerName = cust.name,
                    type = "DEBT_PAYMENT",
                    amount = amount,
                    paymentMethod = if (isCash) PaymentMethod.CASH else PaymentMethod.E_WALLET,
                    referenceId = "",
                    notes = notes.ifEmpty { "سند سداد مديونية" },
                    userId = user.id,
                    userName = user.name,
                    balanceAfter = newDebt
                )
            )
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "DEBT_PAYMENT",
                    entityType = "CUSTOMER",
                    entityId = cust.name,
                    previousValue = "${cust.currentDebt} YER",
                    newValue = "$newDebt YER",
                    notes = "سداد دين بمبلغ $amount ريال للعميل ${cust.name}"
                )
            )
            snackbarMessage.value = "تم تسجيل سند السداد بنجاح"
        }
    }

    fun recordWaste(materialId: String, qty: Double, unit: String, reason: String, notes: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val mat = db.rawMaterialDao().getRawMaterialById(materialId) ?: return@launch
            inventoryEngine.deductRawMaterial(
                materialId = materialId,
                amount = qty,
                unit = unit,
                referenceId = "WASTE-${System.currentTimeMillis() % 10000}",
                userId = user.id,
                userName = user.name,
                txType = InventoryTxType.WASTE,
                note = "تسجيل هدر وتلف ($reason): $notes"
            )
            db.wasteDao().insertWaste(
                WasteTransactionEntity(
                    rawMaterialId = materialId,
                    rawMaterialName = mat.name,
                    quantity = qty,
                    unit = unit,
                    reason = WasteReason.SPOILAGE,
                    estimatedCost = qty * mat.lastPurchasePrice,
                    notes = "$reason: $notes",
                    userId = user.id,
                    userName = user.name
                )
            )
            snackbarMessage.value = "تم خصم الهدر والتالف من المخزون"
        }
    }

    fun applyStockAdjustment(materialId: String, actualStock: Double, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val mat = db.rawMaterialDao().getRawMaterialById(materialId) ?: return@launch
            val diff = actualStock - mat.currentStock
            db.rawMaterialDao().updateStock(materialId, actualStock)
            db.inventoryTransactionDao().insertTransaction(
                InventoryTransactionEntity(
                    rawMaterialId = materialId,
                    rawMaterialName = mat.name,
                    type = InventoryTxType.ADJUSTMENT,
                    quantityChange = diff,
                    unit = mat.baseUnit,
                    previousQuantity = mat.currentStock,
                    newQuantity = actualStock,
                    notes = "تسوية جردية: $reason",
                    userId = user.id,
                    userName = user.name
                )
            )
            snackbarMessage.value = "تم تطبيق التسوية وتحديث رصيد المخزون"
        }
    }

    fun voidSale(saleId: String, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = salesEngine.voidSale(saleId, reason, user)
            result.onSuccess {
                snackbarMessage.value = "تم إلغاء الفاتورة ${it.invoiceNumber} وإرجاع المخزون وتسوية الحسابات بنجاح"
            }.onFailure {
                snackbarMessage.value = "تعذر إلغاء الفاتورة: ${it.localizedMessage}"
            }
        }
    }

    suspend fun askAi(prompt: String): String {
        return powerAiEngine.processQuery(prompt)
    }
}
