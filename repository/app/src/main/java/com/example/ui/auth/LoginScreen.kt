package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.UserProfile
import com.example.ui.theme.PowerOrange
import com.example.ui.theme.PowerOrangeDark
import com.example.ui.theme.StatusDanger

@Composable
fun LoginScreen(
    userProfiles: List<UserProfile>,
    onAuthenticate: (userId: String, pin: String) -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    LaunchedEffect(enteredPin, selectedUser, errorMessage) {
        isAuthenticating = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(PowerOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "Power Home",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "POWER FEUL POS",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = PowerOrangeDark
                        )
                        Text(
                            text = "كافتيريا نادي Power Home Gym",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Fast user switcher row
                Text(
                    text = "اختر حساب الموظف للمتابعة:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(userProfiles) { user ->
                        val isSelected = selectedUser?.id == user.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedUser = if (isSelected) null else user
                                enteredPin = ""
                                onClearError()
                                localError = null
                            },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("(${user.role.titleAr})", fontSize = 11.sp, color = PowerOrange)
                                        if (user.isLocked) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.LockClock,
                                                contentDescription = "مقفل",
                                                tint = StatusDanger,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PowerOrange.copy(alpha = 0.2f),
                                selectedLabelColor = PowerOrangeDark
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Locked alert for selected user
                if (selectedUser?.isLocked == true) {
                    Surface(
                        color = StatusDanger.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.LockClock, contentDescription = null, tint = StatusDanger)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الحساب مقفل مؤقتاً لتكرار المحاولات الخاطئة. يرجى الانتظار (${selectedUser!!.lockRemainingSeconds} ثانية).",
                                color = StatusDanger,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // PIN Display Dots (4 to 6 digits)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val maxDots = 6
                    for (i in 0 until maxDots) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) PowerOrange
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Error alert
                val currentError = errorMessage ?: localError
                if (currentError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Numeric Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keyRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )

                    for (row in keyRows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            for (key in row) {
                                KeypadButton(
                                    text = key,
                                    onClick = {
                                        when (key) {
                                            "C" -> {
                                                enteredPin = ""
                                                localError = null
                                                onClearError()
                                            }
                                            "DEL" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                                localError = null
                                                onClearError()
                                            }
                                            else -> {
                                                if (enteredPin.length < 6) {
                                                    enteredPin += key
                                                    localError = null
                                                    onClearError()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Explicit Login Button
                val isLoginReady = selectedUser != null && enteredPin.length >= 4 && selectedUser?.isLocked != true && !isAuthenticating
                Button(
                    onClick = {
                        if (selectedUser == null) {
                            localError = "يرجى تحديد حساب الموظف أولاً"
                        } else if (enteredPin.length < 4) {
                            localError = "رمز PIN يجب أن يتكون من 4 إلى 6 أرقام"
                        } else if (!isAuthenticating) {
                            isAuthenticating = true
                            localError = null
                            val userId = selectedUser!!.id
                            val pin = enteredPin
                            enteredPin = ""
                            onAuthenticate(userId, pin)
                        }
                    },
                    enabled = isLoginReady,
                    colors = ButtonDefaults.buttonColors(containerColor = PowerOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button")
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تسجيل الدخول", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    val isAction = text == "C" || text == "DEL"
    Surface(
        modifier = Modifier
            .size(width = 80.dp, height = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("keypad_$text"),
        color = if (isAction) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "DEL") {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "مسح",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (text == "C") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
