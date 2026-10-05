package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.*
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import com.example.ui.theme.*

@Composable
fun AdminDashboardTab(
    sales: List<SaleEntity>,
    rawMaterials: List<RawMaterialEntity>,
    customers: List<CustomerEntity>,
    shifts: List<ShiftEntity>
) {
    val totalRevenue = remember(sales) { sales.filter { it.status == "COMPLETED" }.sumOf { it.netAmount } }
    val cashRevenue = remember(sales) { sales.filter { it.status == "COMPLETED" && it.paymentMethod == PaymentMethod.CASH }.sumOf { it.netAmount } }
    val walletRevenue = remember(sales) { sales.filter { it.status == "COMPLETED" && it.paymentMethod == PaymentMethod.E_WALLET }.sumOf { it.netAmount } }
    val debtRevenue = remember(sales) { sales.filter { it.status == "COMPLETED" && it.paymentMethod == PaymentMethod.DEBT }.sumOf { it.netAmount } }
    val totalDebtsOutstanding = remember(customers) { customers.sumOf { it.currentDebt } }
    val lowStockCount = remember(rawMaterials) { rawMaterials.count { it.currentStock <= it.minStock } }
    val openShift = remember(shifts) { shifts.firstOrNull { it.status == ShiftStatus.OPEN } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("نظرة عامة على كافتيريا POWER FEUL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("مؤشرات الأداء المباشرة والمبيعات والمخزون", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Row of KPI Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "إجمالي المبيعات",
                    value = "${totalRevenue.toLong()} ريال",
                    subtitle = "${sales.size} فاتورة مسجلة",
                    icon = Icons.Default.PointOfSale,
                    accentColor = PowerOrange,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "إجمالي الديون القائمة",
                    value = "${totalDebtsOutstanding.toLong()} ريال",
                    subtitle = "${customers.count { it.currentDebt > 0 }} عميل مدين",
                    icon = Icons.Default.CreditCard,
                    accentColor = StatusDanger,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "تنبيهات المخزون",
                    value = "$lowStockCount مواد",
                    subtitle = if (lowStockCount > 0) "بحاجة لإعادة شراء" else "المخزون ممتاز",
                    icon = Icons.Default.Warning,
                    accentColor = if (lowStockCount > 0) StatusWarning else StatusSuccess,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "حالة الشفت",
                    value = if (openShift != null) "شفت #${openShift.shiftNumber}" else "مغلق",
                    subtitle = if (openShift != null) openShift.userName else "لا يوجد كاشير نشط",
                    icon = Icons.Default.Schedule,
                    accentColor = if (openShift != null) StatusSuccess else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Payment Breakdown
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("توزيع طرق الدفع", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PaymentBreakdownBox("💵 نقدي (Cash)", "${cashRevenue.toLong()} ريال", StatusSuccess, Modifier.weight(1f))
                        PaymentBreakdownBox("📱 محفظة (E-Wallet)", "${walletRevenue.toLong()} ريال", StatusInfo, Modifier.weight(1f))
                        PaymentBreakdownBox("🧾 دين (Debt)", "${debtRevenue.toLong()} ريال", StatusDanger, Modifier.weight(1f))
                    }
                }
            }
        }

        // Recent Invoices
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("أحدث العمليات والفواتير", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (sales.isEmpty()) {
                        Text("لا توجد مبيعات مسجلة بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        sales.take(5).forEach { sale ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PowerOrange.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Receipt, contentDescription = null, tint = PowerOrange, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(sale.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${sale.userName} • ${sale.paymentMethod.titleAr}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    "${sale.netAmount.toLong()} ريال",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = if (sale.status == "VOIDED") StatusDanger else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PaymentBreakdownBox(title: String, amount: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(amount, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun AdminSalesTab(
    sales: List<SaleEntity>,
    onVoidSale: (saleId: String, reason: String) -> Unit,
    userRole: UserRole
) {
    var saleToVoid by remember { mutableStateOf<SaleEntity?>(null) }
    var voidReason by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("سجل المبيعات والفواتير", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("جميع الفواتير الصادرة مع إمكانية الإلغاء/الإرجاع مع تدوين السبب واسترجاع المخزون", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            if (sales.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد مبيعات مسجلة حتى الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sales) { sale ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(sale.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (sale.status == "VOIDED") {
                                        Surface(color = StatusDanger.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                            Text("ملغاة / مسترجعة", color = StatusDanger, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    } else {
                                        Surface(color = StatusSuccess.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                            Text("ناجحة", color = StatusSuccess, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text("الكاشير: ${sale.userName} • وسيلة الدفع: ${sale.paymentMethod.titleAr} ${if (sale.customerName != null) "(العميل: ${sale.customerName})" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (sale.status == "VOIDED" && sale.voidReason != null) {
                                    Text("سبب الإلغاء: ${sale.voidReason}", fontSize = 11.sp, color = StatusDanger)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${sale.netAmount.toLong()} ريال", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (sale.status == "VOIDED") MaterialTheme.colorScheme.outline else PowerOrange)
                                Spacer(modifier = Modifier.width(12.dp))

                                if (sale.status != "VOIDED" && userRole.canVoidSales) {
                                    OutlinedButton(
                                        onClick = { saleToVoid = sale },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                                    ) {
                                        Text("إلغاء / استرجاع", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (saleToVoid != null) {
        Dialog(onDismissRequest = { saleToVoid = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("إلغاء الفاتورة ${saleToVoid!!.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = StatusDanger)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ملاحظة أمنية: يتم تسجيل حركة استرجاع Void، وإرجاع المواد الخام تلقائياً للمخزون وتسوية حساب الشفت والديون.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("سبب الإلغاء (إلزامي)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { saleToVoid = null }, modifier = Modifier.weight(1f)) {
                            Text("تراجع")
                        }
                        Button(
                            onClick = {
                                if (voidReason.isNotBlank()) {
                                    onVoidSale(saleToVoid!!.id, voidReason)
                                    saleToVoid = null
                                    voidReason = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تأكيد الإلغاء")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminProductsTab(
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    onAddProduct: (name: String, catId: String, price: Double) -> Unit,
    onUpdateProductPrice: (productId: String, newPrice: Double) -> Unit,
    onToggleProductAvailable: (productId: String, isAvailable: Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editPriceProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var newPriceText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("إدارة المنتجات وقائمة الأسعار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("إضافة وتعديل المنتجات وأسعار البيع وتحديد التصنيف المناسب", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة منتج جديد")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(products) { prod ->
                    val catName = categories.firstOrNull { it.id == prod.categoryId }?.name ?: "عام"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("التصنيف: $catName • رمز SKU: ${prod.sku.ifEmpty { "غير محدد" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (prod.costPrice > 0) {
                                Text("التكلفة التقديرية: ${prod.costPrice.toLong()} ريال • هامش الربح: ${((prod.price - prod.costPrice) / prod.price * 100).toInt()}%", fontSize = 11.sp, color = StatusSuccess)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${prod.price.toLong()} ريال", fontWeight = FontWeight.Black, fontSize = 16.sp, color = PowerOrange)
                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(onClick = {
                                editPriceProduct = prod
                                newPriceText = prod.price.toLong().toString()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل السعر", tint = MaterialTheme.colorScheme.primary)
                            }

                            Switch(
                                checked = prod.isAvailable,
                                onCheckedChange = { onToggleProductAvailable(prod.id, it) }
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var priceText by remember { mutableStateOf("") }
        var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("إضافة منتج جديد", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PowerOrange)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المنتج") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("سعر البيع (ريال يمني)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("اختر التصنيف:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCatId == cat.id,
                                onClick = { selectedCatId = cat.id },
                                label = { Text(cat.name, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val p = priceText.toDoubleOrNull() ?: 0.0
                                if (name.isNotBlank() && p > 0) {
                                    onAddProduct(name, selectedCatId, p)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إضافة المنتج")
                        }
                    }
                }
            }
        }
    }

    if (editPriceProduct != null) {
        Dialog(onDismissRequest = { editPriceProduct = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("تعديل سعر ${editPriceProduct!!.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPriceText,
                        onValueChange = { newPriceText = it },
                        label = { Text("السعر الجديد (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { editPriceProduct = null }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val p = newPriceText.toDoubleOrNull()
                                if (p != null && p > 0) {
                                    onUpdateProductPrice(editPriceProduct!!.id, p)
                                    editPriceProduct = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ السعر")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminRecipesTab(
    recipes: List<RecipeEntity>,
    products: List<ProductEntity>,
    rawMaterials: List<RawMaterialEntity>,
    mixtures: List<MixtureEntity>
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Recipes, 1: Mixtures

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("الوصفات والخلطات المعيارية (Recipes & Blends)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("تحديد نسب ومقادير كل مشروب لحساب التكلفة الدقيقة وخصم المخزون آلياً عند كل عملية بيع", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            TabRow(
                selectedTabIndex = selectedSection,
                modifier = Modifier.width(260.dp)
            ) {
                Tab(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    text = { Text("الوصفات (${recipes.size})") }
                )
                Tab(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    text = { Text("الخلطات (${mixtures.size})") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            if (selectedSection == 0) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(recipes) { recipe ->
                        val product = products.firstOrNull { it.id == recipe.productId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(recipe.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = PowerOrange.copy(alpha = 0.15f)) {
                                        Text("الإصدار v${recipe.version}", fontSize = 10.sp, color = PowerOrange, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text("المنتج المرتبط: ${product?.name ?: "غير محدد"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("التكلفة المعيارية: ${recipe.calculatedCost.toLong()} ريال", fontWeight = FontWeight.Bold, color = StatusSuccess)
                                if (product != null) {
                                    Text("سعر البيع: ${product.price.toLong()} ريال", fontSize = 12.sp)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(mixtures) { mix ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(mix.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("الكمية الناتجة: ${mix.outputQuantity} ${mix.unit} • ملاحظات: ${mix.notes.ifEmpty { "لا توجد" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("إجمالي التكلفة: ${mix.totalCost.toLong()} ريال", fontWeight = FontWeight.Bold, color = StatusSuccess)
                                Text("تكلفة الوحدة: ${mix.unitCost} ريال / ${mix.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
