package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.admin.AdminMainScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.SessionLockDialog
import com.example.ui.pos.PaymentDialog
import com.example.ui.pos.PosScreen
import com.example.ui.pos.ReceiptDialog
import com.example.ui.shift.ShiftCloseDialog
import com.example.ui.shift.ShiftHandoverDialog
import com.example.ui.shift.ShiftStartDialog
import com.example.ui.theme.PowerFeulTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PowerFeulTheme {
                val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()
                val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(snackbarMessage) {
                    snackbarMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.snackbarMessage.value = null
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val isSessionLocked by viewModel.isSessionLocked.collectAsStateWithLifecycle()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        viewModel.onUserActivity()
                                    }
                                }
                            }
                    ) {
                        if (currentUser == null) {
                            val userProfiles by viewModel.userProfiles.collectAsStateWithLifecycle()
                            val loginError by viewModel.loginErrorMessage.collectAsStateWithLifecycle()
                            LoginScreen(
                                userProfiles = userProfiles,
                                onAuthenticate = { userId, pin -> viewModel.authenticate(userId, pin) },
                                errorMessage = loginError,
                                onClearError = { viewModel.loginErrorMessage.value = null }
                            )
                        } else {
                            if (isSessionLocked) {
                                val loginError by viewModel.loginErrorMessage.collectAsStateWithLifecycle()
                                SessionLockDialog(
                                    currentUser = currentUser!!,
                                    onUnlock = { pin -> viewModel.unlockSession(pin) },
                                    onLogout = { viewModel.logout() },
                                    errorMessage = loginError
                                )
                            }

                            val loggedInUser = currentUser!!
                            when (activeScreen) {
                                AppScreen.POS -> {
                                    val currentShift by viewModel.currentShift.collectAsStateWithLifecycle()
                                    val categories by viewModel.categories.collectAsStateWithLifecycle()
                                    val products by viewModel.products.collectAsStateWithLifecycle()
                                    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()

                                    PosScreen(
                                        currentUser = loggedInUser,
                                        currentShift = currentShift,
                                        categories = categories,
                                        products = products,
                                        cartItems = cartItems,
                                        onAddToCart = { p -> viewModel.addToCart(p) },
                                        onRemoveFromCart = { p -> viewModel.removeFromCart(p) },
                                        onClearCart = { viewModel.clearCart() },
                                        onOpenPayDialog = { viewModel.showPaymentDialog.value = true },
                                        onOpenStartShift = { viewModel.showStartShiftDialog.value = true },
                                        onOpenCloseShift = { viewModel.showCloseShiftDialog.value = true },
                                        onNavigateToAdmin = { viewModel.navigateTo(AppScreen.ADMIN) },
                                        onLogout = { viewModel.logout() }
                                    )
                                }
                                AppScreen.ADMIN -> {
                                    val sales by viewModel.sales.collectAsStateWithLifecycle()
                                    val products by viewModel.products.collectAsStateWithLifecycle()
                                    val categories by viewModel.categories.collectAsStateWithLifecycle()
                                    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
                                    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
                                    val mixtures by viewModel.mixtures.collectAsStateWithLifecycle()
                                    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
                                    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
                                    val customers by viewModel.customers.collectAsStateWithLifecycle()
                                    val allShifts by viewModel.allShifts.collectAsStateWithLifecycle()
                                    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
                                    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
                                    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()

                                    AdminMainScreen(
                                        currentUser = loggedInUser,
                                        onBackToPos = { viewModel.navigateTo(AppScreen.POS) },
                                        onLogout = { viewModel.logout() },
                                        sales = sales,
                                        products = products,
                                        categories = categories,
                                        rawMaterials = rawMaterials,
                                        recipes = recipes,
                                        mixtures = mixtures,
                                        suppliers = suppliers,
                                        purchases = purchases,
                                        customers = customers,
                                        shifts = allShifts,
                                        expenses = expenses,
                                        auditLogs = auditLogs,
                                        users = allUsers,
                                        onAddProduct = { name, catId, price -> viewModel.addProduct(name, catId, price) },
                                        onUpdateProductPrice = { id, p -> viewModel.updateProductPrice(id, p) },
                                        onToggleProductAvailable = { id, avail -> viewModel.toggleProductAvailable(id, avail) },
                                        onAddRawMaterial = { name, sku, unit, minStock, price -> viewModel.addRawMaterial(name, sku, unit, minStock, price) },
                                        onCreatePurchase = { sId, sName, inv, mId, q, u, p ->
                                            viewModel.createPurchase(sId, sName, inv, mId, q, u, p)
                                        },
                                        onAddCustomer = { name, phone, limit, allowDebt -> viewModel.addCustomer(name, phone, limit, allowDebt) },
                                        onRecordDebtPayment = { cId, amt, isCash, notes ->
                                            viewModel.recordDebtPayment(cId, amt, isCash, notes)
                                        },
                                        onAddEmployee = { name, user, pin, role, phone -> viewModel.addEmployee(name, user, pin, role, phone) },
                                        onToggleEmployeeActive = { uId, active -> viewModel.toggleUserActive(uId, active) },
                                        onAddExpense = { title, cat, amt, isCash, paidTo, notes -> viewModel.addExpense(title, cat, amt, isCash, paidTo, notes) },
                                        onRecordWaste = { mId, q, u, reason, notes ->
                                            viewModel.recordWaste(mId, q, u, reason, notes)
                                        },
                                        onApplyStockAdjustment = { mId, actual, reason ->
                                            viewModel.applyStockAdjustment(mId, actual, reason)
                                        },
                                        onVoidSale = { saleId, reason ->
                                            viewModel.voidSale(saleId, reason)
                                        },
                                        onAskAi = { prompt -> viewModel.askAi(prompt) }
                                    )
                                }
                            }

                            // Dialogs
                            val showStartShiftDialog by viewModel.showStartShiftDialog.collectAsStateWithLifecycle()
                            if (showStartShiftDialog) {
                                ShiftStartDialog(
                                    cashierName = loggedInUser.name,
                                    onConfirm = { cash, notes -> viewModel.startShift(cash, notes) },
                                    onDismiss = { viewModel.showStartShiftDialog.value = false }
                                )
                            }

                            val showCloseShiftDialog by viewModel.showCloseShiftDialog.collectAsStateWithLifecycle()
                            if (showCloseShiftDialog) {
                                val currentShift by viewModel.currentShift.collectAsStateWithLifecycle()
                                if (currentShift != null) {
                                    ShiftCloseDialog(
                                        shift = currentShift!!,
                                        onConfirmClose = { actual, handedOver, left, notes ->
                                            viewModel.closeShift(actual, handedOver, left, notes)
                                        },
                                        onDismiss = { viewModel.showCloseShiftDialog.value = false }
                                    )
                                }
                            }

                            val showHandoverDialog by viewModel.showHandoverDialog.collectAsStateWithLifecycle()
                            if (showHandoverDialog) {
                                val allShifts by viewModel.allShifts.collectAsStateWithLifecycle()
                                if (allShifts.isNotEmpty()) {
                                    val prevShift = allShifts.first()
                                    ShiftHandoverDialog(
                                        previousShift = prevShift,
                                        currentCashierName = loggedInUser.name,
                                        onConfirmHandover = { actualReceived, notes ->
                                            viewModel.acceptHandover(actualReceived, notes)
                                        },
                                        onDismiss = { viewModel.showHandoverDialog.value = false }
                                    )
                                }
                            }

                            val showPaymentDialog by viewModel.showPaymentDialog.collectAsStateWithLifecycle()
                            if (showPaymentDialog) {
                                val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
                                val eligibleDebtCustomers by viewModel.eligibleDebtCustomers.collectAsStateWithLifecycle()
                                val totalCart = remember(cartItems) { cartItems.sumOf { it.totalPrice } }
                                PaymentDialog(
                                    totalAmount = totalCart,
                                    eligibleCustomers = eligibleDebtCustomers,
                                    userRole = loggedInUser.role,
                                    onConfirmSale = { method, cashReceived, ref, custId ->
                                        viewModel.executeSale(method, cashReceived, ref, custId)
                                    },
                                    onDismiss = { viewModel.showPaymentDialog.value = false }
                                )
                            }

                            val lastCompletedSale by viewModel.lastCompletedSale.collectAsStateWithLifecycle()
                            if (lastCompletedSale != null) {
                                ReceiptDialog(
                                    sale = lastCompletedSale!!,
                                    onNewSale = { viewModel.lastCompletedSale.value = null },
                                    onPrint = { viewModel.snackbarMessage.value = "جاري إرسال الفاتورة إلى طابعة البلوتوث/الشبكة..." },
                                    onDismiss = { viewModel.lastCompletedSale.value = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
