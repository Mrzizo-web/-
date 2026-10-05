package com.example.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.CartItem
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.ShiftStatus
import com.example.ui.theme.PowerOrange
import com.example.ui.theme.PowerOrangeDark
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    currentUser: UserEntity,
    currentShift: ShiftEntity?,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    cartItems: List<CartItem>,
    onAddToCart: (ProductEntity) -> Unit,
    onRemoveFromCart: (ProductEntity) -> Unit,
    onClearCart: () -> Unit,
    onOpenPayDialog: () -> Unit,
    onOpenStartShift: () -> Unit,
    onOpenCloseShift: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenuDropdown by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, selectedCategoryId, searchQuery) {
        products.filter { p ->
            p.isActive &&
                    (selectedCategoryId == null || p.categoryId == selectedCategoryId) &&
                    (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.sku.contains(searchQuery, ignoreCase = true))
        }
    }

    val totalCartPrice = remember(cartItems) {
        cartItems.sumOf { it.totalPrice }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "POWER FEUL POS",
                            fontWeight = FontWeight.Black,
                            color = PowerOrangeDark,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(16.dp))

                        // Cashier info badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = PowerOrange)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(currentUser.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (currentShift != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("• شفت #${currentShift.shiftNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Shift status chip
                        if (currentShift != null && currentShift.status == ShiftStatus.OPEN) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "🟢 الشفت نشط",
                                    color = StatusSuccess,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusDanger.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "🔴 لا يوجد شفت مفتوح",
                                    color = StatusDanger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("بحث عن منتج...", fontSize = 12.sp) },
                        modifier = Modifier
                            .width(220.dp)
                            .height(48.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (currentUser.role.canAccessAdmin) {
                        FilledTonalButton(
                            onClick = onNavigateToAdmin,
                            modifier = Modifier.testTag("admin_portal_button")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("لوحة الإدارة")
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenuDropdown = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "قائمة الشفت")
                        }
                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false }
                        ) {
                            if (currentShift == null || currentShift.status != ShiftStatus.OPEN) {
                                DropdownMenuItem(
                                    text = { Text("بدء شفت جديد", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = StatusSuccess) },
                                    onClick = {
                                        showMenuDropdown = false
                                        onOpenStartShift()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("إنهاء الشفت الحالي", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null, tint = StatusDanger) },
                                    onClick = {
                                        showMenuDropdown = false
                                        onOpenCloseShift()
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("تسجيل الخروج") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                                onClick = {
                                    showMenuDropdown = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Right / Center Main Area: Categories & Products Grid (approx 65% width)
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                // Category Tabs
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("الكل", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PowerOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PowerOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Product Cards Grid
                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد منتجات متطابقة مع التصنيف أو البحث", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts) { prod ->
                            PosProductCard(
                                product = prod,
                                onAdd = { onAddToCart(prod) }
                            )
                        }
                    }
                }
            }

            // Left / Side Area: Cart Panel (approx 35% width)
            Card(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 12.dp, start = 4.dp, end = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Cart Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PowerOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("طلب العميل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = PowerOrange) {
                                Text("${cartItems.sumOf { it.quantity }}")
                            }
                        }

                        if (cartItems.isNotEmpty()) {
                            TextButton(onClick = onClearCart) {
                                Text("إفراغ", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Cart Items List
                    if (cartItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocalMall, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("السلة فارغة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                Text("اضغط على أي صنف لإضافته للطلب", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cartItems) { item ->
                                PosCartItemRow(
                                    item = item,
                                    onIncrease = { onAddToCart(item.product) },
                                    onDecrease = { onRemoveFromCart(item.product) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Order Summary & Total
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الإجمالي الكلي:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${totalCartPrice.toLong()} ريال",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = PowerOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big Touch Payment Button
                    Button(
                        onClick = onOpenPayDialog,
                        enabled = cartItems.isNotEmpty() && currentShift?.status == ShiftStatus.OPEN,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("checkout_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PowerOrange,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (currentShift?.status != ShiftStatus.OPEN) "يرجى فتح الشفت أولاً للبيع" else "الدفع (${totalCartPrice.toLong()} ريال)",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosProductCard(
    product: ProductEntity,
    onAdd: () -> Unit
) {
    val isEnabled = product.isAvailable

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = isEnabled, onClick = onAdd)
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEnabled) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )

                if (!isEnabled) {
                    Surface(
                        color = StatusDanger.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "غير متوفر",
                            color = StatusDanger,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${product.price.toLong()} ريال",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = if (isEnabled) PowerOrange else MaterialTheme.colorScheme.outline
                )

                if (isEnabled) {
                    Surface(
                        shape = CircleShape,
                        color = PowerOrange.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة", tint = PowerOrange, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Block, contentDescription = "غير متوفر", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosCartItemRow(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text("${item.product.price.toLong()} ريال × ${item.quantity} = ${item.totalPrice.toLong()} ريال", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "تقليل", modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "${item.quantity}",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "زيادة", modifier = Modifier.size(16.dp), tint = PowerOrange)
                }
            }
        }
    }
}
