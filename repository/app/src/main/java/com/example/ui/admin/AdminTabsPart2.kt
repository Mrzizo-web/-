package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.*
import com.example.domain.model.CustomerStatus
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminInventoryTab(
    rawMaterials: List<RawMaterialEntity>,
    onAddRawMaterial: (name: String, sku: String, baseUnit: String, minStock: Double, price: Double) -> Unit,
    onRecordWaste: (materialId: String, qty: Double, unit: String, reason: String, notes: String) -> Unit,
    onApplyStockAdjustment: (materialId: String, actualStock: Double, reason: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var wasteMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var adjustMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("المخزون والمواد الخام", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("مراقبة أرصدة المواد، تسجيل الهدر والتلف، والتسويات الجردية", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة مادة خام جديدة")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rawMaterials) { mat ->
                    val isCritical = mat.currentStock <= 0.0
                    val isLow = mat.currentStock <= mat.minStock && !isCritical

                    val statusColor = when {
                        isCritical -> StatusDanger
                        isLow -> StatusWarning
                        else -> StatusSuccess
                    }
                    val statusText = when {
                        isCritical -> "🔴 نافد تماماً"
                        isLow -> "🟡 منخفض"
                        else -> "🟢 متوفر"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(mat.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(alpha = 0.15f)) {
                                    Text(statusText, fontSize = 11.sp, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text("الرصيد الحالي: ${mat.currentStock} ${mat.baseUnit} • الحد الأدنى: ${mat.minStock} ${mat.baseUnit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("سعر التوريد: ${mat.lastPurchasePrice.toLong()} ريال / ${mat.baseUnit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { wasteMaterial = mat },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text("تسجيل هدر", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { adjustMaterial = mat },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text("جرد وتعديل", fontSize = 11.sp)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // Add Raw Material Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var sku by remember { mutableStateOf("") }
        var baseUnit by remember { mutableStateOf("KG") }
        var minStockText by remember { mutableStateOf("5") }
        var priceText by remember { mutableStateOf("1000") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("إضافة مادة خام جديدة للمخزن", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PowerOrange)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المادة الخام (مثل: موز، حليب، مكسرات)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("رمز SKU (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("وحدة القياس الأساسية:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("KG" to "كجم", "LITER" to "لتر", "G" to "جرام", "ML" to "مل", "PIECE" to "حبة").forEach { (uKey, uLabel) ->
                            FilterChip(
                                selected = baseUnit == uKey,
                                onClick = { baseUnit = uKey },
                                label = { Text(uLabel, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = minStockText,
                            onValueChange = { minStockText = it },
                            label = { Text("الحد الأدنى للتنبيه") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("سعر الوحدة (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val minS = minStockText.toDoubleOrNull() ?: 5.0
                                val p = priceText.toDoubleOrNull() ?: 0.0
                                if (name.isNotBlank() && p >= 0) {
                                    onAddRawMaterial(name, sku, baseUnit, minS, p)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إضافة المادة")
                        }
                    }
                }
            }
        }
    }

    // Waste Dialog
    if (wasteMaterial != null) {
        var wasteQtyText by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("تلف طبيعي") }
        var notes by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { wasteMaterial = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("تسجيل هدر / تالف: ${wasteMaterial!!.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = StatusDanger)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = wasteQtyText,
                        onValueChange = { wasteQtyText = it },
                        label = { Text("الكمية المهدرة (${wasteMaterial!!.baseUnit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("السبب (تلف / انتهاء صلاحية / خطأ تحضير)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { wasteMaterial = null }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val qty = wasteQtyText.toDoubleOrNull()
                                if (qty != null && qty > 0) {
                                    onRecordWaste(wasteMaterial!!.id, qty, wasteMaterial!!.baseUnit, reason, notes)
                                    wasteMaterial = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("خصم الهدر")
                        }
                    }
                }
            }
        }
    }

    // Adjustment Dialog
    if (adjustMaterial != null) {
        var actualStockText by remember { mutableStateOf(adjustMaterial!!.currentStock.toString()) }
        var reason by remember { mutableStateOf("جرد دوري") }

        Dialog(onDismissRequest = { adjustMaterial = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("تسوية جرد فعلي: ${adjustMaterial!!.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("الرصيد في النظام: ${adjustMaterial!!.currentStock} ${adjustMaterial!!.baseUnit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = actualStockText,
                        onValueChange = { actualStockText = it },
                        label = { Text("الكمية الفعلية الموجودة في المخزن") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("سبب التسوية") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { adjustMaterial = null }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val actual = actualStockText.toDoubleOrNull()
                                if (actual != null && actual >= 0) {
                                    onApplyStockAdjustment(adjustMaterial!!.id, actual, reason)
                                    adjustMaterial = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ التسوية")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPurchasesTab(
    purchases: List<PurchaseEntity>,
    suppliers: List<SupplierEntity>,
    rawMaterials: List<RawMaterialEntity>,
    onCreatePurchase: (supplierId: String, supplierName: String, invoiceNo: String, materialId: String, qty: Double, unit: String, unitPrice: Double) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("المشتريات والتوريد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("فواتير الشراء تزيد من المخزون آلياً وتعيد احتساب تكاليف الوصفات فورياً", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تسجيل شراء مواد")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            if (purchases.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد فواتير شراء سابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(purchases) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("فاتورة #${p.invoiceNumber} • المورد: ${p.supplierName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("المسؤول: ${p.createdByUserName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${p.totalAmount.toLong()} ريال", fontWeight = FontWeight.Black, fontSize = 16.sp, color = StatusSuccess)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var supplierId by remember { mutableStateOf(suppliers.firstOrNull()?.id ?: "") }
        var invoiceNo by remember { mutableStateOf("INV-${System.currentTimeMillis() % 10000}") }
        var materialId by remember { mutableStateOf(rawMaterials.firstOrNull()?.id ?: "") }
        var qtyText by remember { mutableStateOf("10") }
        var unitPriceText by remember { mutableStateOf("1000") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("تسجيل شراء مواد خام من مورد", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PowerOrange)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = invoiceNo,
                        onValueChange = { invoiceNo = it },
                        label = { Text("رقم الفاتورة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("اختر المورد:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suppliers) { sup ->
                            FilterChip(
                                selected = supplierId == sup.id,
                                onClick = { supplierId = sup.id },
                                label = { Text(sup.name, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("اختر المادة الخام:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rawMaterials) { mat ->
                            FilterChip(
                                selected = materialId == mat.id,
                                onClick = { materialId = mat.id },
                                label = { Text("${mat.name} (${mat.baseUnit})", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it },
                            label = { Text("الكمية") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unitPriceText,
                            onValueChange = { unitPriceText = it },
                            label = { Text("سعر الوحدة (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val s = suppliers.firstOrNull { it.id == supplierId } ?: suppliers.firstOrNull()
                                val m = rawMaterials.firstOrNull { it.id == materialId } ?: rawMaterials.firstOrNull()
                                val q = qtyText.toDoubleOrNull() ?: 0.0
                                val p = unitPriceText.toDoubleOrNull() ?: 0.0
                                if (s != null && m != null && q > 0 && p > 0) {
                                    onCreatePurchase(s.id, s.name, invoiceNo, m.id, q, m.baseUnit, p)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تأكيد التوريد")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDebtsTab(
    customers: List<CustomerEntity>,
    onAddCustomer: (name: String, phone: String, creditLimit: Double, allowDebt: Boolean) -> Unit,
    onRecordDebtPayment: (customerId: String, amount: Double, isCash: Boolean, notes: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var payCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("العملاء وحسابات الديون", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("الحدود الائتمانية والمديونيات الحالية وتسجيل سندات القبض والسداد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة عميل جديد")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(customers) { c ->
                    val isExceeded = c.currentDebt >= c.creditLimit
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (isExceeded) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(color = StatusDanger.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text("تجاوز الحد الائتماني", color = StatusDanger, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text("هاتف: ${c.phone.ifEmpty { "غير متوفر" }} • الحد المسموح: ${c.creditLimit.toLong()} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("الرصيد: ${c.currentDebt.toLong()} ريال", fontWeight = FontWeight.Black, fontSize = 15.sp, color = if (c.currentDebt > 0) StatusDanger else StatusSuccess)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            if (c.currentDebt > 0) {
                                Button(
                                    onClick = { payCustomer = c },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                                ) {
                                    Text("تسجيل سداد", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var creditLimitText by remember { mutableStateOf("15000") }
        var allowDebt by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("إضافة عميل جديد للنادي", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PowerOrange)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم العميل الثلاثي") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = it },
                        label = { Text("الحد الائتماني المسموح به (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val limit = creditLimitText.toDoubleOrNull() ?: 15000.0
                                if (name.isNotBlank()) {
                                    onAddCustomer(name, phone, limit, allowDebt)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تسجيل العميل")
                        }
                    }
                }
            }
        }
    }

    if (payCustomer != null) {
        var paymentAmountText by remember { mutableStateOf(payCustomer!!.currentDebt.toLong().toString()) }
        var isCash by remember { mutableStateOf(true) }
        var notes by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { payCustomer = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("سداد دين: ${payCustomer!!.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = StatusSuccess)
                    Text("الرصيد المتبقي عليه: ${payCustomer!!.currentDebt.toLong()} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = { Text("المبلغ المسدد (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isCash,
                            onClick = { isCash = true },
                            label = { Text("💵 نقدي") }
                        )
                        FilterChip(
                            selected = !isCash,
                            onClick = { isCash = false },
                            label = { Text("📱 محفظة") }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { payCustomer = null }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val amount = paymentAmountText.toDoubleOrNull()
                                if (amount != null && amount > 0) {
                                    onRecordDebtPayment(payCustomer!!.id, amount, isCash, notes)
                                    payCustomer = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تأكيد السداد")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminEmployeesTab(
    users: List<UserEntity>,
    onAddEmployee: (name: String, username: String, pin: String, role: UserRole, phone: String) -> Unit,
    onToggleActive: (userId: String, isActive: Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("إدارة الموظفين والصلاحيات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("إضافة كاشير ومشرفين، تحديد الأدوار، وإيقاف الحسابات", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة موظف جديد")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(users) { u ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = PowerOrange.copy(alpha = 0.15f)) {
                                    Text(u.role.titleAr, fontSize = 11.sp, color = PowerOrange, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text("اسم المستخدم: @${u.username} • هاتف: ${u.phone.ifEmpty { "غير مسجل" }} • رمز PIN: محمي ومشفّر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (u.isActive) "نشط" else "معطل", fontSize = 12.sp, color = if (u.isActive) StatusSuccess else StatusDanger)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = u.isActive,
                                onCheckedChange = { onToggleActive(u.id, it) }
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
        var username by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf(UserRole.CASHIER) }
        var phone by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("إضافة موظف جديد", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PowerOrange)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم الموظف الكامل") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("اسم الدخول (Username)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6) pin = it },
                            label = { Text("رمز PIN (4-6 أرقام)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("الدور والصلاحية:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(UserRole.CASHIER, UserRole.SUPERVISOR, UserRole.ADMIN, UserRole.INVENTORY_MANAGER).forEach { r ->
                            FilterChip(
                                selected = selectedRole == r,
                                onClick = { selectedRole = r },
                                label = { Text(r.titleAr, fontSize = 12.sp) }
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
                                if (name.isNotBlank() && username.isNotBlank() && pin.length >= 4) {
                                    onAddEmployee(name, username, pin, selectedRole, phone)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إضافة الموظف")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminExpensesTab(
    expenses: List<ExpenseEntity>,
    onAddExpense: (title: String, category: String, amount: Double, isCash: Boolean, paidTo: String, notes: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("المصروفات النثرية والتشغيلية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("تسجيل وتتبع مصاريف الصيانة، النظافة، التغليف، والضيافة مفصولة عن المبيعات", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تسجيل مصروف جديد")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            if (expenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد مصروفات مسجلة بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(expenses) { exp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(exp.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("التصنيف: ${exp.category} • طريقة الدفع: ${exp.paymentMethod.titleAr} • تم الصرف بواسطة: ${exp.userName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${exp.amount.toLong()} ريال", fontWeight = FontWeight.Black, fontSize = 16.sp, color = StatusDanger)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("نظافة ومواد استهلاكية") }
        var amountText by remember { mutableStateOf("") }
        var isCash by remember { mutableStateOf(true) }
        var paidTo by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("تسجيل مصروف جديد", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PowerOrange)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("بيان المصروف (مثل: شراء أكياس، صيانة عصارة)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ (ريال يمني)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isCash,
                            onClick = { isCash = true },
                            label = { Text("💵 صرف من نقدية الدرج") }
                        )
                        FilterChip(
                            selected = !isCash,
                            onClick = { isCash = false },
                            label = { Text("📱 تحويل محفظة") }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val amt = amountText.toDoubleOrNull() ?: 0.0
                                if (title.isNotBlank() && amt > 0) {
                                    onAddExpense(title, category, amt, isCash, paidTo, notes)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تسجيل المصروف")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminShiftsTab(shifts: List<ShiftEntity>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("سجل الشفتات وتدقيق النقدية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("متابعة العجز والفائض النقدي وحركات التسليم والاستلام بين الموظفين", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(shifts) { shift ->
                    val hasDiscrepancy = shift.status == ShiftStatus.DISCREPANCY || Math.abs(shift.discrepancyAmount) > 0.01

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("شفت #${shift.shiftNumber} — ${shift.userName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (hasDiscrepancy) StatusDanger.copy(alpha = 0.15f) else StatusSuccess.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = shift.status.titleAr,
                                        fontSize = 10.sp,
                                        color = if (hasDiscrepancy) StatusDanger else StatusSuccess,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "افتتاحية: ${shift.openingCash.toLong()} ريال • مبيعات كاش: ${shift.totalCashSales.toLong()} ريال • إلكتروني: ${shift.totalWalletSales.toLong()} ريال • ديون: ${shift.totalDebtSales.toLong()} ريال",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (shift.status != ShiftStatus.OPEN) {
                                Text(
                                    "المسلم للمشرف: ${shift.handedOverCash.toLong()} ريال • المتروك للتالي: ${shift.leftForNextShiftCash.toLong()} ريال",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (hasDiscrepancy) {
                                Text("الفرق: ${shift.discrepancyAmount.toLong()} ريال", fontWeight = FontWeight.Black, fontSize = 15.sp, color = StatusDanger)
                            } else {
                                Text("نقدية متطابقة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = StatusSuccess)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun AdminReportsTab(
    sales: List<SaleEntity>,
    products: List<ProductEntity>,
    rawMaterials: List<RawMaterialEntity>
) {
    val totalRevenue = sales.filter { it.status == "COMPLETED" }.sumOf { it.netAmount }
    val totalCost = sales.filter { it.status == "COMPLETED" }.sumOf { it.totalCost }
    val estimatedProfit = (totalRevenue - totalCost).coerceAtLeast(0.0)
    val marginPercent = if (totalRevenue > 0) ((estimatedProfit / totalRevenue) * 100).toInt() else 0

    Column(modifier = Modifier.fillMaxSize()) {
        Text("التقارير المالية والربحية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("تحليل الإيرادات، التكاليف التشغيلية، وهامش الربح الصافي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("إجمالي الإيرادات", "${totalRevenue.toLong()} ريال", "من المبيعات الصافية", Icons.AutoMirrored.Filled.TrendingUp, PowerOrange, Modifier.weight(1f))
            KpiCard("تكلفة البضاعة المباعة (COGS)", "${totalCost.toLong()} ريال", "تكلفة المواد المستهلكة", Icons.AutoMirrored.Filled.ReceiptLong, StatusWarning, Modifier.weight(1f))
            KpiCard("هامش الربح الإجمالي", "${estimatedProfit.toLong()} ريال", "نسبة الربح: $marginPercent%", Icons.Default.AttachMoney, StatusSuccess, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("تحليل ربحية المنتجات الأكثر طلباً", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(products) { prod ->
                        val profit = (prod.price - prod.costPrice).coerceAtLeast(0.0)
                        val pMargin = if (prod.price > 0) ((profit / prod.price) * 100).toInt() else 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("سعر البيع: ${prod.price.toLong()} ريال • التكلفة: ${prod.costPrice.toLong()} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("هامش الربح: ${profit.toLong()} ريال ($pMargin%)", fontWeight = FontWeight.Bold, color = StatusSuccess, fontSize = 13.sp)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogTab(auditLogs: List<AuditLogEntity>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("سجل التدقيق والعمليات الحساسة (Audit Log)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("سجل مشفر غير قابل للتعديل يوثق كل عملية بيع، إغلاق شفت، تعديل سعر، أو سداد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(auditLogs) { log ->
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
                    val dateStr = sdf.format(Date(log.timestamp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(log.action, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(log.notes, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("المستخدم: ${log.userName} (${log.userRole}) • الكيان: ${log.entityType} ${log.entityId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun AdminPowerAiTab(onAskAi: suspend (String) -> String) {
    var queryText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = PowerOrange.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = PowerOrange)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("مساعد POWER AI التحليلي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = PowerOrange)
                Text("تحليل فوري دقيق ومبني 100% على بيانات قاعدة البيانات الحقيقية بدون تخمين", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Quick Prompts
        Text("أسئلة سريعة شائعة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "كم بعنا اليوم؟",
                "ما المواد التي تحتاج شراء قريب؟",
                "ما تكلفة Power Full الحالية؟",
                "فروقات الشفتات والنقدية"
            ).forEach { prompt ->
                AssistChip(
                    onClick = {
                        queryText = prompt
                        isLoading = true
                        coroutineScope.launch {
                            responseText = onAskAi(prompt)
                            isLoading = false
                        }
                    },
                    label = { Text(prompt, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = { Text("اكتب سؤالك هنا عن المبيعات، المخزون، الديون، أو التكاليف...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_query_input"),
                singleLine = true
            )
            Button(
                onClick = {
                    if (queryText.isNotBlank()) {
                        isLoading = true
                        coroutineScope.launch {
                            responseText = onAskAi(queryText)
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && queryText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("ai_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("اسأل POWER AI", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("إجابة المساعد الذكي:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PowerOrange)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("جاري فحص وتدقيق بيانات قاعدة البيانات...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (responseText != null) {
                    Text(
                        text = responseText!!,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "اضغط على أحد الأسئلة السريعة أعلاه أو اكتب أي استفسار تريده حول أداء الكافتيريا.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(
    users: List<UserEntity>,
    onExportDatabase: () -> Unit,
    onRestoreDatabase: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("إعدادات النظام والكافتيريا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("بيانات المنشأة، العملة، صلاحيات الموظفين، والنسخ الاحتياطي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("بيانات الكافتيريا الرسمية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("اسم الكافتيريا: POWER FEUL", fontSize = 14.sp)
                Text("النادي: Power Home Gym", fontSize = 14.sp)
                Text("العملة الرسمية: الريال اليمني (YER)", fontSize = 14.sp)
                Text("الجهاز المستهدف: Samsung Galaxy Tab S7+ (شاشة لمس Tablet)", fontSize = 14.sp)

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("الموظفون المعتمدون (${users.size} موظف):", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                users.forEach { u ->
                    Text("• ${u.name} — الدور: ${u.role.titleAr}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("النسخ الاحتياطي والاستعادة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "صدّر نسخة من بيانات المبيعات والمخزون إلى ملف، أو استعد نسخة موثوقة. الاستعادة غير متاحة أثناء شفت مفتوح وستُعاد تشغيل الواجهة بعدها.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onExportDatabase, colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير نسخة")
                    }
                    OutlinedButton(onClick = onRestoreDatabase) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استعادة نسخة")
                    }
                }
            }
        }
    }
}
