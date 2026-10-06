package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.*
import com.example.domain.model.UserRole
import com.example.security.AppPermission
import com.example.security.PermissionChecker
import com.example.ui.theme.PowerOrange
import com.example.ui.theme.PowerOrangeDark

enum class AdminTab(val titleAr: String, val icon: ImageVector, val requiredPermission: AppPermission) {
    DASHBOARD("الرئيسية", Icons.Default.Dashboard, AppPermission.ACCESS_ADMIN),
    SALES("المبيعات", Icons.Default.PointOfSale, AppPermission.ACCESS_ADMIN),
    PRODUCTS("المنتجات", Icons.Default.Inventory2, AppPermission.MANAGE_PRODUCTS),
    RECIPES("الوصفات والخلطات", Icons.Default.RestaurantMenu, AppPermission.MANAGE_RECIPES),
    INVENTORY("المخزون والمواد", Icons.Default.Warehouse, AppPermission.MANAGE_INVENTORY),
    PURCHASES("المشتريات والتوريد", Icons.Default.ShoppingBag, AppPermission.MANAGE_INVENTORY),
    DEBTS("العملاء والديون", Icons.Default.CreditCard, AppPermission.MANAGE_CUSTOMERS),
    EMPLOYEES("الموظفون", Icons.Default.People, AppPermission.MANAGE_EMPLOYEES),
    SHIFTS("الشفتات والنقدية", Icons.Default.AccountBalanceWallet, AppPermission.VIEW_REPORTS),
    EXPENSES("المصروفات", Icons.Default.MoneyOff, AppPermission.VIEW_REPORTS),
    REPORTS("التقارير والأرباح", Icons.Default.BarChart, AppPermission.VIEW_REPORTS),
    AUDIT_LOG("سجل العمليات", Icons.Default.History, AppPermission.VIEW_AUDIT_LOGS),
    POWER_AI("مساعد POWER AI", Icons.Default.SmartToy, AppPermission.ACCESS_ADMIN),
    SETTINGS("الإعدادات", Icons.Default.Settings, AppPermission.MANAGE_SETTINGS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainScreen(
    currentUser: UserEntity,
    onBackToPos: () -> Unit,
    onLogout: () -> Unit,
    // Database State
    sales: List<SaleEntity>,
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    rawMaterials: List<RawMaterialEntity>,
    recipes: List<RecipeEntity>,
    mixtures: List<MixtureEntity>,
    suppliers: List<SupplierEntity>,
    purchases: List<PurchaseEntity>,
    customers: List<CustomerEntity>,
    shifts: List<ShiftEntity>,
    expenses: List<ExpenseEntity>,
    auditLogs: List<AuditLogEntity>,
    users: List<UserEntity>,
    // Actions
    onAddProduct: (name: String, catId: String, price: Double) -> Unit,
    onUpdateProductPrice: (productId: String, newPrice: Double) -> Unit,
    onToggleProductAvailable: (productId: String, isAvailable: Boolean) -> Unit,
    onAddRawMaterial: (name: String, sku: String, baseUnit: String, minStock: Double, price: Double) -> Unit,
    onCreatePurchase: (supplierId: String, supplierName: String, invoiceNo: String, materialId: String, qty: Double, unit: String, unitPrice: Double) -> Unit,
    onAddCustomer: (name: String, phone: String, creditLimit: Double, allowDebt: Boolean) -> Unit,
    onRecordDebtPayment: (customerId: String, amount: Double, isCash: Boolean, notes: String) -> Unit,
    onAddEmployee: (name: String, username: String, pin: String, role: UserRole, phone: String) -> Unit,
    onToggleEmployeeActive: (userId: String, isActive: Boolean) -> Unit,
    onAddExpense: (title: String, category: String, amount: Double, isCash: Boolean, paidTo: String, notes: String) -> Unit,
    onRecordWaste: (materialId: String, qty: Double, unit: String, reason: String, notes: String) -> Unit,
    onApplyStockAdjustment: (materialId: String, actualStock: Double, reason: String) -> Unit,
    onVoidSale: (saleId: String, reason: String) -> Unit,
    onExportDatabase: () -> Unit,
    onRestoreDatabase: () -> Unit,
    onAskAi: suspend (String) -> String
) {
    // Strict RBAC check at view level: Cashier cannot access
    if (!currentUser.role.canAccessAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.widthIn(max = 500.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("غير مصرح بالدخول", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("حساب الكاشير ليس لديه صلاحية الوصول إلى لوحة تحكم الإدارة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onBackToPos,
                        colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
                    ) {
                        Text("العودة إلى شاشة البيع POS")
                    }
                }
            }
        }
        return
    }

    val availableTabs = remember(currentUser.role) {
        AdminTab.values().filter { PermissionChecker.hasPermission(currentUser.role, it.requiredPermission) }
    }

    var selectedTab by remember(currentUser.role) {
        mutableStateOf(availableTabs.firstOrNull() ?: AdminTab.DASHBOARD)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("POWER FEUL — لوحة الإدارة", fontWeight = FontWeight.Black, color = PowerOrangeDark)
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PowerOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${currentUser.name} (${currentUser.role.titleAr})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PowerOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToPos) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة للبيع")
                    }
                },
                actions = {
                    FilledTonalButton(onClick = onBackToPos) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("شاشة الكاشير")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "تسجيل خروج")
                    }
                }
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sidebar Navigation (Tablet landscape)
            Surface(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableTabs) { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { selectedTab = tab }
                                .testTag("admin_tab_${tab.name}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PowerOrange else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = tab.titleAr,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    AdminTab.DASHBOARD -> AdminDashboardTab(sales, rawMaterials, customers, shifts)
                    AdminTab.SALES -> AdminSalesTab(sales, onVoidSale, currentUser.role)
                    AdminTab.PRODUCTS -> AdminProductsTab(products, categories, onAddProduct, onUpdateProductPrice, onToggleProductAvailable)
                    AdminTab.RECIPES -> AdminRecipesTab(recipes, products, rawMaterials, mixtures, PermissionChecker.hasPermission(currentUser.role, AppPermission.VIEW_COSTS))
                    AdminTab.INVENTORY -> AdminInventoryTab(rawMaterials, onAddRawMaterial, onRecordWaste, onApplyStockAdjustment)
                    AdminTab.PURCHASES -> AdminPurchasesTab(purchases, suppliers, rawMaterials, onCreatePurchase)
                    AdminTab.DEBTS -> AdminDebtsTab(customers, onAddCustomer, onRecordDebtPayment)
                    AdminTab.EMPLOYEES -> AdminEmployeesTab(users, onAddEmployee, onToggleEmployeeActive)
                    AdminTab.SHIFTS -> AdminShiftsTab(shifts)
                    AdminTab.EXPENSES -> AdminExpensesTab(expenses, onAddExpense)
                    AdminTab.REPORTS -> AdminReportsTab(sales, products, rawMaterials)
                    AdminTab.AUDIT_LOG -> AdminAuditLogTab(auditLogs)
                    AdminTab.POWER_AI -> AdminPowerAiTab(onAskAi)
                    AdminTab.SETTINGS -> AdminSettingsTab(users, onExportDatabase, onRestoreDatabase)
                }
            }
        }
    }
}
