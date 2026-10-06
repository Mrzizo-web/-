package com.example.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.ShiftEntity
import com.example.ui.theme.PowerOrange
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun ShiftStartDialog(
    cashierName: String,
    onConfirm: (openingCash: Double, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var openingCashText by remember { mutableStateOf("50000") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "بداية الشفت",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = PowerOrange
                )
                Text(
                    text = "مرحباً $cashierName. أدخل النقدية الموجودة في الدرج قبل بدء العمل:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = openingCashText,
                    onValueChange = {
                        openingCashText = it
                        errorMessage = null
                    },
                    label = { Text("النقدية الافتتاحية (ريال يمني)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_cash_input"),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(20000, 30000, 50000, 100000).forEach { amount ->
                        FilledTonalButton(
                            onClick = { openingCashText = amount.toString() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("${amount / 1000}k", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            val cash = openingCashText.toDoubleOrNull()
                            if (cash == null || cash < 0) {
                                errorMessage = "يرجى إدخال مبلغ نقدي صحيح"
                            } else {
                                onConfirm(cash, notes)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_shift_button")
                    ) {
                        Text("بدء الشفت", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftCloseDialog(
    shift: ShiftEntity,
    onConfirmClose: (actualCash: Double, handedOverCash: Double, leftForNextShiftCash: Double, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    val expectedInDrawer = shift.openingCash + shift.totalCashSales - shift.totalExpensesCash

    var actualCashText by remember { mutableStateOf(expectedInDrawer.toLong().toString()) }
    var handedOverText by remember { mutableStateOf(shift.totalCashSales.toLong().toString()) }
    var leftForNextText by remember { mutableStateOf(shift.openingCash.toLong().toString()) }
    var notes by remember { mutableStateOf("") }
    var errorWarning by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "ملخص إنهاء الشفت #${shift.shiftNumber}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = PowerOrange
                )
                Text(
                    text = "الكاشير: ${shift.userName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Summary Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMetricBox("الافتتاحية", "${shift.openingCash.toLong()} ريال", modifier = Modifier.weight(1f))
                    SummaryMetricBox("المبيعات النقدية", "${shift.totalCashSales.toLong()} ريال", modifier = Modifier.weight(1f))
                    SummaryMetricBox("المتوقع بالدرج", "${expectedInDrawer.toLong()} ريال", isHighlight = true, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Actual Cash
                OutlinedTextField(
                    value = actualCashText,
                    onValueChange = {
                        actualCashText = it
                        errorWarning = null
                    },
                    label = { Text("النقدية الموجودة فعلياً في الدرج (ريال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("actual_cash_input"),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = handedOverText,
                        onValueChange = {
                            handedOverText = it
                            errorWarning = null
                        },
                        label = { Text("المسلم للمشرف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("handed_over_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = leftForNextText,
                        onValueChange = {
                            leftForNextText = it
                            errorWarning = null
                        },
                        label = { Text("المتروك للعامل التالي") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("left_for_next_input"),
                        singleLine = true
                    )
                }

                val actualCash = actualCashText.toDoubleOrNull() ?: 0.0
                val handedOver = handedOverText.toDoubleOrNull() ?: 0.0
                val leftForNext = leftForNextText.toDoubleOrNull() ?: 0.0
                val sumSplit = handedOver + leftForNext
                val splitMismatch = Math.abs(sumSplit - actualCash) > 0.01
                val discrepancy = actualCash - expectedInDrawer

                Spacer(modifier = Modifier.height(12.dp))

                if (splitMismatch) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تنبيه: مجموع المسلم للمشرف (${handedOver.toLong()}) والمتروك (${leftForNext.toLong()}) يساوي ${sumSplit.toLong()} ولا يطابق النقدية الفعلية (${actualCash.toLong()})!",
                                color = StatusWarning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (Math.abs(discrepancy) > 0.01) {
                    val color = if (discrepancy > 0) StatusSuccess else StatusDanger
                    val textDesc = if (discrepancy > 0) "يوجد فائض نقدي بمقدار +${discrepancy.toLong()} ريال" else "يوجد عجز نقدي بمقدار ${discrepancy.toLong()} ريال"
                    Text(
                        text = textDesc,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات إغلاق الشفت") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (errorWarning != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorWarning!!, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            if (splitMismatch) {
                                errorWarning = "يرجى موازنة مبلغ التسليم والمبلغ المتروك ليطابق النقدية الفعلية"
                            } else {
                                onConfirmClose(actualCash, handedOver, leftForNext, notes)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_close_shift_button")
                    ) {
                        Text("تأكيد إنهاء الشفت", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftHandoverDialog(
    previousShift: ShiftEntity,
    currentCashierName: String,
    onConfirmHandover: (actualReceived: Double, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var actualReceivedText by remember { mutableStateOf(previousShift.leftForNextShiftCash.toLong().toString()) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "استلام الشفت",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = PowerOrange
                )
                Text(
                    text = "المبلغ المتوقع في الدرج من الشفت السابق (${previousShift.userName}): ${previousShift.leftForNextShiftCash.toLong()} ريال",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = actualReceivedText,
                    onValueChange = {
                        actualReceivedText = it
                        error = null
                    },
                    label = { Text("النقدية الموجودة فعلياً في الدرج") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )

                val received = actualReceivedText.toDoubleOrNull() ?: 0.0
                val diff = received - previousShift.leftForNextShiftCash
                if (Math.abs(diff) > 0.01) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يوجد فرق: ${if (diff > 0) "+${diff.toLong()}" else diff.toLong()} ريال يمني. يرجى توضيح السبب في الملاحظات.",
                        color = StatusWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الاستلام") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            val amount = actualReceivedText.toDoubleOrNull()
                            if (amount == null || amount < 0) {
                                error = "يرجى إدخال مبلغ صحيح"
                            } else {
                                onConfirmHandover(amount, notes)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تأكيد الاستلام وبدء الشفت", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryMetricBox(
    title: String,
    value: String,
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (isHighlight) PowerOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isHighlight) PowerOrange else MaterialTheme.colorScheme.onSurface)
        }
    }
}
