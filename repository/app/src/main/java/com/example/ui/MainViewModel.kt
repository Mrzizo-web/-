package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.*
import com.example.data.backup.DatabaseBackupManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.seed.DatabaseSeeder
import com.example.domain.model.CustomerStatus
import com.example.domain.model.InventoryTxType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.model.WasteReason
import com.example.security.AppPermission
import com.example.security.LockoutPolicy
import com.example.security.PasswordHasher
import com.example.security.PermissionChecker
import com.example.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import android.net.Uri

enum class AppScreen {
    POS,
    ADMIN
}

sealed class AuthResult {
    data class Success(val user: UserEntity) : AuthResult()
    data class Failure(val message: String, val remainingAttempts: Int? = null) : AuthResult()
    data class AccountLocked(val remainingSeconds: Long) : AuthResult()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val db = AppDatabase.getInstance(application)

    val passwordHasher = PasswordHasher.DEFAULT
    val lockoutPolicy = LockoutPolicy()
    val sessionManager = SessionManager(viewModelScope)
    val isSessionLocked = sessionManager.isSessionLocked

    fun onUserActivity() {
        sessionManager.onUserActivity()
    }

    fun unlockSession(pin: String, onComplete: (AuthResult) -> Unit = {}) {
        val user = _currentUser.value ?: return
        authenticate(user.id, pin) { result ->
            if (result is AuthResult.Success) {
                sessionManager.unlockSession()
            }
            onComplete(result)
        }
    }

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
    val userProfiles: StateFlow<List<UserProfile>> = db.userDao().getAllActiveUsers()
        .map { list ->
            val now = System.currentTimeMillis()
            list.map { u ->
                UserProfile(
                    id = u.id,
                    name = u.name,
                    username = u.username,
                    role = u.role,
                    phone = u.phone,
                    isActive = u.isActive,
                    isLocked = lockoutPolicy.isLocked(u.lockedUntil, now),
                    lockRemainingSeconds = lockoutPolicy.remainingLockTimeSeconds(u.lockedUntil, now),
                    lastLoginAt = u.lastLoginAt
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun authenticate(userId: String, rawPin: String, onResult: (AuthResult) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val user = db.userDao().getUserById(userId)
            if (user == null) {
                val res = AuthResult.Failure("المستخدم المحدد غير موجود في النظام")
                loginErrorMessage.value = res.message
                withContext(Dispatchers.Main) { onResult(res) }
                return@launch
            }

            if (!user.isActive) {
                val res = AuthResult.Failure("تم تعطيل هذا الحساب، يرجى مراجعة إدارة الكافتيريا")
                loginErrorMessage.value = res.message
                withContext(Dispatchers.Main) { onResult(res) }
                return@launch
            }

            if (lockoutPolicy.isLocked(user.lockedUntil, now)) {
                val remainingSec = lockoutPolicy.remainingLockTimeSeconds(user.lockedUntil, now)
                val res = AuthResult.AccountLocked(remainingSec)
                loginErrorMessage.value = "الحساب مقفل مؤقتاً لتكرار المحاولات الخاطئة. المتبقي: $remainingSec ثانية"
                db.auditLogDao().insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = "LOGIN_FAILED",
                        entityType = "USER",
                        entityId = user.username,
                        notes = "محاولة تسجيل دخول إلى حساب مقفل مؤقتاً"
                    )
                )
                withContext(Dispatchers.Main) { onResult(res) }
                return@launch
            }

            val isValid = passwordHasher.verify(rawPin, user.pinHash, user.pinSalt)
            if (!isValid) {
                val newAttempts = user.failedAttempts + 1
                val lockUntil = lockoutPolicy.calculateLockout(newAttempts, now)
                db.userDao().updateFailedAttempts(user.id, newAttempts, lockUntil, now)

                val action = if (lockUntil != null) "ACCOUNT_LOCKED" else "LOGIN_FAILED"
                val notes = if (lockUntil != null) {
                    "تم قفل الحساب مؤقتاً لمدة ${lockoutPolicy.lockDurationMillis / 60000} دقائق بعد $newAttempts محاولات فاشلة متتالية"
                } else {
                    "محاولة دخول فاشلة برمز PIN خاطئ (المحاولة $newAttempts من ${lockoutPolicy.maxFailedAttempts})"
                }

                db.auditLogDao().insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = action,
                        entityType = "USER",
                        entityId = user.username,
                        notes = notes
                    )
                )

                val res = if (lockUntil != null) {
                    val remainingSec = lockoutPolicy.remainingLockTimeSeconds(lockUntil, now)
                    val msg = "تم تجاوز الحد الأقصى للمحاولات الخاطئة. أُقفل الحساب لمدة $remainingSec ثانية."
                    loginErrorMessage.value = msg
                    AuthResult.AccountLocked(remainingSec)
                } else {
                    val remaining = (lockoutPolicy.maxFailedAttempts - newAttempts).coerceAtLeast(0)
                    val msg = "رمز PIN غير صحيح. المحاولات المتبقية: $remaining"
                    loginErrorMessage.value = msg
                    AuthResult.Failure(msg, remaining)
                }

                withContext(Dispatchers.Main) { onResult(res) }
                return@launch
            }

            // Authentication Successful
            db.userDao().recordSuccessfulLogin(user.id, now)
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "LOGIN_SUCCESS",
                    entityType = "USER",
                    entityId = user.username,
                    notes = "تسجيل دخول ناجح إلى النظام"
                )
            )

            val updatedUser = user.copy(
                failedAttempts = 0,
                lockedUntil = null,
                lastLoginAt = now,
                updatedAt = now
            )

            _currentUser.value = updatedUser
            loginErrorMessage.value = null
            _activeScreen.value = AppScreen.POS
            sessionManager.unlockSession()

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

            withContext(Dispatchers.Main) { onResult(AuthResult.Success(updatedUser)) }
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
        sessionManager.unlockSession()
    }

    private suspend fun logUnauthorizedAttempt(user: UserEntity, permission: AppPermission, operationName: String) {
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.titleAr,
                action = "UNAUTHORIZED_ACCESS_ATTEMPT",
                entityType = "PERMISSION",
                entityId = permission.name,
                notes = "محاولة غير مصرح بها لتنفيذ: $operationName (الصلاحية المطلوبة: ${permission.titleAr})"
            )
        )
        snackbarMessage.value = "غير مصرح لك بهذه العملية (${permission.titleAr})"
    }

    fun navigateTo(screen: AppScreen) {
        val user = _currentUser.value
        if (screen == AppScreen.ADMIN && !PermissionChecker.hasPermission(user, AppPermission.ACCESS_ADMIN)) {
            if (user != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    logUnauthorizedAttempt(user, AppPermission.ACCESS_ADMIN, "دخول لوحة الإدارة")
                }
            }
            snackbarMessage.value = "غير مصرح لك بالوصول إلى لوحة الإدارة"
            return
        }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_PRODUCTS)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_PRODUCTS, "إضافة منتج $name")
                return@launch
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_PRODUCTS)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_PRODUCTS, "تعديل سعر المنتج")
                return@launch
            }
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
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_PRODUCTS)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_PRODUCTS, "تعديل حالة توفر المنتج")
                return@launch
            }
            db.productDao().updateAvailability(productId, isAvailable)
        }
    }

    fun addRawMaterial(name: String, sku: String, baseUnit: String, minStock: Double, price: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_INVENTORY)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_INVENTORY, "إضافة مادة خام $name")
                return@launch
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_CUSTOMERS)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_CUSTOMERS, "إضافة عميل جديد $name")
                return@launch
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_EMPLOYEES)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_EMPLOYEES, "إضافة موظف جديد $name")
                return@launch
            }
            val existing = db.userDao().getUserByUsername(username)
            if (existing != null) {
                snackbarMessage.value = "اسم المستخدم @$username مستخدم بالفعل"
                return@launch
            }
            val hashResult = passwordHasher.hash(pin)
            val newUser = UserEntity(
                name = name,
                username = username,
                pinHash = hashResult.hashHex,
                pinSalt = hashResult.saltHex,
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
                    notes = "إضافة موظف جديد: $name بدور ${role.titleAr}"
                )
            )
            snackbarMessage.value = "تم إضافة الموظف بنجاح"
        }
    }

    fun toggleUserActive(userId: String, isActive: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_EMPLOYEES)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_EMPLOYEES, "تعديل حالة حساب موظف")
                return@launch
            }

            val targetUser = db.userDao().getUserById(userId) ?: return@launch
            if (!isActive) {
                // Protect last OWNER
                if (targetUser.role == UserRole.OWNER) {
                    val activeOwners = db.userDao().countActiveOwners()
                    if (activeOwners <= 1) {
                        snackbarMessage.value = "لا يمكن تعطيل مالك النظام الوحيد النشط حالياً"
                        return@launch
                    }
                }

                // Protect self-deactivation if last active admin/owner
                if (userId == user.id && (targetUser.role == UserRole.OWNER || targetUser.role == UserRole.ADMIN)) {
                    val activeAdmins = db.userDao().countActiveAdminsAndOwners()
                    if (activeAdmins <= 1) {
                        snackbarMessage.value = "لا يمكن تعطيل حسابك لأنه آخر حساب إداري نشط في النظام"
                        return@launch
                    }
                }
            }

            db.userDao().setUserActiveStatus(userId, isActive)
            val action = if (isActive) "USER_ENABLED" else "USER_DISABLED"
            val stateText = if (isActive) "تفعيل" else "تعطيل"
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = action,
                    entityType = "USER",
                    entityId = targetUser.username,
                    notes = "$stateText حساب الموظف: ${targetUser.name} (@${targetUser.username})"
                )
            )
            snackbarMessage.value = if (isActive) "تم تفعيل حساب الموظف" else "تم تعطيل حساب الموظف"
        }
    }

    fun changeUserPin(
        userId: String,
        currentPin: String?,
        newPin: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val targetUser = db.userDao().getUserById(userId)
            if (targetUser == null) {
                withContext(Dispatchers.Main) { onResult(false, "المستخدم غير موجود") }
                return@launch
            }

            val isSelf = user.id == userId
            if (!isSelf && !PermissionChecker.hasPermission(user, AppPermission.MANAGE_EMPLOYEES)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_EMPLOYEES, "تغيير رمز PIN لموظف آخر")
                withContext(Dispatchers.Main) { onResult(false, "غير مصرح لك بتغيير رمز PIN لهذا الموظف") }
                return@launch
            }

            // If changing own PIN, verify current PIN
            if (isSelf) {
                if (currentPin.isNullOrBlank() || !passwordHasher.verify(currentPin, targetUser.pinHash, targetUser.pinSalt)) {
                    withContext(Dispatchers.Main) { onResult(false, "رمز PIN الحالي غير صحيح") }
                    return@launch
                }
            }

            if (newPin.length !in 4..6 || !newPin.all { it.isDigit() }) {
                withContext(Dispatchers.Main) { onResult(false, "رمز PIN الجديد يجب أن يتكون من 4 إلى 6 أرقام") }
                return@launch
            }

            val newHash = passwordHasher.hash(newPin)
            db.userDao().updatePin(userId, newHash.hashHex, newHash.saltHex)

            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    userRole = user.role.titleAr,
                    action = "PIN_CHANGED",
                    entityType = "USER",
                    entityId = targetUser.username,
                    notes = "تم تحديث رمز PIN للمستخدم ${targetUser.name} بنجاح"
                )
            )

            withContext(Dispatchers.Main) {
                snackbarMessage.value = "تم تغيير رمز PIN بنجاح"
                onResult(true, "تم تغيير رمز PIN بنجاح")
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_INVENTORY)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_INVENTORY, "تسجيل أمر شراء وتوريد")
                return@launch
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_DEBTS)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_DEBTS, "تسجيل سند سداد دين")
                return@launch
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.ADJUST_STOCK)) {
                logUnauthorizedAttempt(user, AppPermission.ADJUST_STOCK, "تسجيل هدر وتالف")
                return@launch
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.ADJUST_STOCK)) {
                logUnauthorizedAttempt(user, AppPermission.ADJUST_STOCK, "تسوية جردية للمخزون")
                return@launch
            }
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
            if (!PermissionChecker.hasPermission(user, AppPermission.VOID_SALE)) {
                logUnauthorizedAttempt(user, AppPermission.VOID_SALE, "إلغاء واسترجاع الفاتورة $saleId")
                return@launch
            }
            val result = salesEngine.voidSale(saleId, reason, user)
            result.onSuccess {
                snackbarMessage.value = "تم إلغاء الفاتورة ${it.invoiceNumber} وإرجاع المخزون وتسوية الحسابات بنجاح"
            }.onFailure {
                snackbarMessage.value = "تعذر إلغاء الفاتورة: ${it.localizedMessage}"
            }
        }
    }

    fun exportDatabase(destination: Uri) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_SETTINGS)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_SETTINGS, "تصدير نسخة احتياطية")
                return@launch
            }
            DatabaseBackupManager.export(getApplication(), db, destination)
                .onSuccess { snackbarMessage.value = "تم تصدير النسخة الاحتياطية بنجاح" }
                .onFailure { snackbarMessage.value = "تعذر تصدير النسخة الاحتياطية: ${it.localizedMessage}" }
        }
    }

    fun restoreDatabase(source: Uri, onRestored: () -> Unit) {
        val user = _currentUser.value ?: return
        if (currentShift.value != null) {
            snackbarMessage.value = "أغلق الشفت الحالي قبل استعادة نسخة احتياطية"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (!PermissionChecker.hasPermission(user, AppPermission.MANAGE_SETTINGS)) {
                logUnauthorizedAttempt(user, AppPermission.MANAGE_SETTINGS, "استعادة نسخة احتياطية")
                return@launch
            }
            DatabaseBackupManager.restore(getApplication(), source)
                .onSuccess {
                    withContext(Dispatchers.Main) { onRestored() }
                }
                .onFailure { snackbarMessage.value = "تعذرت الاستعادة: ${it.localizedMessage}" }
        }
    }

    suspend fun askAi(prompt: String): String {
        return powerAiEngine.processQuery(prompt)
    }
}
