package com.example.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.SaleEntity
import com.example.domain.model.CustomerStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.UserRole
import com.example.ui.theme.PowerOrange
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun PaymentDialog(
    totalAmount: Double,
    eligibleCustomers: List<CustomerEntity>,
    userRole: UserRole,
    onConfirmSale: (method: PaymentMethod, cashReceived: Double, reference: String, customerId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashReceivedText by remember { mutableStateOf(totalAmount.toLong().toString()) }
    var walletReference by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "إتمام الدفع",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "اختر وسيلة الدفع لتأكيد الطلب",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = PowerOrange.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${totalAmount.toLong()} ريال",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = PowerOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big Payment Method Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PaymentMethodTab(
                        title = "💵 نقدي",
                        isSelected = selectedMethod == PaymentMethod.CASH,
                        onClick = {
                            selectedMethod = PaymentMethod.CASH
                            validationError = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pay_cash_tab")
                    )
                    PaymentMethodTab(
                        title = "📱 محفظة إلكترونية",
                        isSelected = selectedMethod == PaymentMethod.E_WALLET,
                        onClick = {
                            selectedMethod = PaymentMethod.E_WALLET
                            validationError = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pay_wallet_tab")
                    )
                    PaymentMethodTab(
                        title = "🧾 دين",
                        isSelected = selectedMethod == PaymentMethod.DEBT,
                        onClick = {
                            selectedMethod = PaymentMethod.DEBT
                            validationError = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pay_debt_tab")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Dynamic Method Body
                when (selectedMethod) {
                    PaymentMethod.CASH -> {
                        val received = cashReceivedText.toDoubleOrNull() ?: 0.0
                        val change = (received - totalAmount).coerceAtLeast(0.0)

                        OutlinedTextField(
                            value = cashReceivedText,
                            onValueChange = {
                                cashReceivedText = it
                                validationError = null
                            },
                            label = { Text("المبلغ المستلم من العميل (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cash_received_input"),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick denomination chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "المبلغ بالضبط" to totalAmount,
                                "+500" to (totalAmount + 500),
                                "+1,000" to (totalAmount + 1000),
                                "+5,000" to (totalAmount + 5000)
                            ).forEach { (label, amount) ->
                                AssistChip(
                                    onClick = { cashReceivedText = amount.toLong().toString() },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Change banner
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (received >= totalAmount) StatusSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("المتبقي للعميل (الباقي):", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${change.toLong()} ريال",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (received >= totalAmount) StatusSuccess else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    PaymentMethod.E_WALLET -> {
                        Text(
                            text = "المبلغ المطلوب تحويله: ${totalAmount.toLong()} ريال عبر المحافظ (ون كاش / جايبي / فلوسك / الكريمي)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = walletReference,
                            onValueChange = { walletReference = it },
                            label = { Text("رقم العملية / الحوالة / Reference No. (اختياري)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wallet_ref_input"),
                            singleLine = true
                        )
                    }

                    PaymentMethod.DEBT -> {
                        Text(
                            text = "اختر العميل المعتمد لتسجيل المديونية في حسابه:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customerSearchQuery,
                            onValueChange = { customerSearchQuery = it },
                            placeholder = { Text("بحث عن عميل بالاسم أو الهاتف...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val filteredCustomers = eligibleCustomers.filter {
                            it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                    it.phone.contains(customerSearchQuery)
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredCustomers) { cust ->
                                val isSelected = selectedCustomer?.id == cust.id
                                val willExceed = cust.currentDebt + totalAmount > cust.creditLimit

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedCustomer = cust
                                            validationError = null
                                        },
                                    color = if (isSelected) PowerOrange.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("هاتف: ${cust.phone.ifEmpty { "غير مسجل" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("الرصيد: ${cust.currentDebt.toLong()} / ${cust.creditLimit.toLong()} ريال", fontSize = 12.sp)
                                            if (willExceed) {
                                                Text("يتجاوز الحد الائتماني!", color = StatusDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("متاح: ${(cust.creditLimit - cust.currentDebt).toLong()} ريال", color = StatusSuccess, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            when (selectedMethod) {
                                PaymentMethod.CASH -> {
                                    val rec = cashReceivedText.toDoubleOrNull() ?: 0.0
                                    if (rec < totalAmount) {
                                        validationError = "المبلغ المستلم أقل من الإجمالي المطلوب"
                                    } else {
                                        onConfirmSale(PaymentMethod.CASH, rec, "", null)
                                    }
                                }
                                PaymentMethod.E_WALLET -> {
                                    onConfirmSale(PaymentMethod.E_WALLET, 0.0, walletReference, null)
                                }
                                PaymentMethod.DEBT -> {
                                    if (selectedCustomer == null) {
                                        validationError = "يرجى تحديد العميل من القائمة أعلاه"
                                    } else {
                                        val cust = selectedCustomer!!
                                        val willExceed = cust.currentDebt + totalAmount > cust.creditLimit
                                        if (willExceed && !userRole.canOverrideCreditLimit) {
                                            validationError = "العميل سيتجاوز حده الائتماني المسموح به (${cust.creditLimit.toLong()} ريال). يتطلب موافقة المدير."
                                        } else {
                                            onConfirmSale(PaymentMethod.DEBT, 0.0, "", cust.id)
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_sale_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تأكيد البيع", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) PowerOrange else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ReceiptDialog(
    sale: SaleEntity,
    onNewSale: () -> Unit,
    onPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(StatusSuccess),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "تمت عملية البيع بنجاح!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = StatusSuccess
                )
                Text(
                    text = "POWER FEUL POS — Power Home Gym",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Invoice details card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ReceiptRow("رقم الفاتورة", sale.invoiceNumber, isBold = true)
                        ReceiptRow("طريقة الدفع", sale.paymentMethod.titleAr)
                        if (sale.customerName != null) {
                            ReceiptRow("العميل (دين)", sale.customerName)
                        }
                        if (sale.paymentMethod == PaymentMethod.CASH) {
                            ReceiptRow("المستلم", "${sale.cashReceived.toLong()} ريال")
                            ReceiptRow("الباقي", "${sale.cashChange.toLong()} ريال")
                        }
                        ReceiptRow("الكاشير", sale.userName)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ReceiptRow("الإجمالي الصافي", "${sale.netAmount.toLong()} ريال", isHighlight = true)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPrint,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("طباعة")
                    }
                    Button(
                        onClick = onNewSale,
                        colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("new_sale_button")
                    ) {
                        Text("طلب جديد", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String, isBold: Boolean = false, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = if (isHighlight) 16.sp else 13.sp,
            fontWeight = if (isBold || isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) PowerOrange else MaterialTheme.colorScheme.onSurface
        )
    }
}
