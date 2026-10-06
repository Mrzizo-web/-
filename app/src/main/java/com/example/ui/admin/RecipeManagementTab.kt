package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.MixtureIngredientDraft
import com.example.data.engine.RecipeIngredientDraft
import com.example.data.local.entity.*
import com.example.ui.theme.PowerOrange
import com.example.ui.theme.StatusSuccess

@Composable
fun RecipeManagementTab(
    recipes: List<RecipeEntity>,
    products: List<ProductEntity>,
    rawMaterials: List<RawMaterialEntity>,
    mixtures: List<MixtureEntity>,
    canViewCosts: Boolean,
    onCreateRecipe: (String, String, String, List<RecipeIngredientDraft>) -> Unit,
    onUpdateRecipe: (String, String, String, List<RecipeIngredientDraft>) -> Unit,
    onToggleRecipeActive: (String, Boolean) -> Unit,
    onDeleteRecipe: (String) -> Unit,
    onCreateMixture: (String, String, Double, String, List<MixtureIngredientDraft>) -> Unit,
    onUpdateMixture: (String, String, String, Double, String, List<MixtureIngredientDraft>) -> Unit,
    onDeleteMixture: (String) -> Unit,
    onLoadRecipeItems: suspend (String) -> List<RecipeItemEntity>,
    onLoadMixtureItems: suspend (String) -> List<MixtureItemEntity>
) {
    var section by remember { mutableStateOf(0) }
    var editRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var editMixture by remember { mutableStateOf<MixtureEntity?>(null) }
    var showRecipeEditor by remember { mutableStateOf(false) }
    var showMixtureEditor by remember { mutableStateOf(false) }
    var deleteRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var deleteMixture by remember { mutableStateOf<MixtureEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("الوصفات والخلطات المعيارية", style = MaterialTheme.typography.titleLarge)
                Text(
                    "إضافة وتعديل المقادير وربطها بالمخزون مع احتساب التكلفة تلقائياً.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TabRow(selectedTabIndex = section, modifier = Modifier.width(280.dp)) {
                Tab(
                    selected = section == 0,
                    onClick = { section = 0 },
                    text = { Text("الوصفات (" + recipes.size + ")") }
                )
                Tab(
                    selected = section == 1,
                    onClick = { section = 1 },
                    text = { Text("الخلطات (" + mixtures.size + ")") }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                if (section == 0) {
                    editRecipe = null
                    showRecipeEditor = true
                } else {
                    editMixture = null
                    showMixtureEditor = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PowerOrange)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (section == 0) "إضافة وصفة" else "إضافة خلطة")
        }

        Spacer(Modifier.height(10.dp))

        Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp)) {
            if (section == 0) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(recipes) { recipe ->
                        val product = products.firstOrNull { it.id == recipe.productId }
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        recipe.name,
                                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                if (recipe.isActive) "نشطة" else "معطلة",
                                                fontSize = 10.sp
                                            )
                                        }
                                    )
                                }
                                Text(
                                    "المنتج: " + (product?.name ?: "غير محدد") +
                                        " • الإصدار v" + recipe.version,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (recipe.notes.isNotBlank()) {
                                    Text(
                                        "التحضير: " + recipe.notes,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (canViewCosts) {
                                    Text(
                                        "التكلفة: " + recipe.calculatedCost.toLong() + " ريال",
                                        color = StatusSuccess,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(onClick = {
                                    editRecipe = recipe
                                    showRecipeEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل الوصفة")
                                }
                                Switch(
                                    checked = recipe.isActive,
                                    onCheckedChange = {
                                        onToggleRecipeActive(recipe.id, it)
                                    }
                                )
                                IconButton(onClick = { deleteRecipe = recipe }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف الوصفة")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(mixtures) { mixture ->
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    mixture.name,
                                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                                )
                                Text(
                                    "الناتج: " + mixture.outputQuantity + " " + mixture.unit,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (mixture.notes.isNotBlank()) {
                                    Text(
                                        "ملاحظات: " + mixture.notes,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (canViewCosts) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "الإجمالي: " + mixture.totalCost.toLong() + " ريال",
                                            color = StatusSuccess,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "الوحدة: " + mixture.unitCost + " / " + mixture.unit,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    editMixture = mixture
                                    showMixtureEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل الخلطة")
                                }
                                IconButton(onClick = { deleteMixture = mixture }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف الخلطة")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showRecipeEditor) {
        RecipeEditorDialog(
            recipe = editRecipe,
            products = products,
            rawMaterials = rawMaterials,
            mixtures = mixtures,
            loadItems = onLoadRecipeItems,
            onDismiss = { showRecipeEditor = false },
            onCreate = onCreateRecipe,
            onUpdate = onUpdateRecipe
        )
    }

    if (showMixtureEditor) {
        MixtureEditorDialog(
            mixture = editMixture,
            rawMaterials = rawMaterials,
            loadItems = onLoadMixtureItems,
            onDismiss = { showMixtureEditor = false },
            onCreate = onCreateMixture,
            onUpdate = onUpdateMixture
        )
    }

    deleteRecipe?.let { recipe ->
        AlertDialog(
            onDismissRequest = { deleteRecipe = null },
            title = { Text("حذف الوصفة") },
            text = {
                Text("سيتم حذف الوصفة ومكوناتها. لا يمكن الحذف إذا كان المنتج متاحاً للبيع.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecipe(recipe.id)
                    deleteRecipe = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteRecipe = null }) { Text("تراجع") }
            }
        )
    }

    deleteMixture?.let { mixture ->
        AlertDialog(
            onDismissRequest = { deleteMixture = null },
            title = { Text("حذف الخلطة") },
            text = { Text("لا يمكن حذف الخلطة إذا كانت مستخدمة داخل وصفة.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMixture(mixture.id)
                    deleteMixture = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteMixture = null }) { Text("تراجع") }
            }
        )
    }
}

@Composable
private fun RecipeEditorDialog(
    recipe: RecipeEntity?,
    products: List<ProductEntity>,
    rawMaterials: List<RawMaterialEntity>,
    mixtures: List<MixtureEntity>,
    loadItems: suspend (String) -> List<RecipeItemEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, List<RecipeIngredientDraft>) -> Unit,
    onUpdate: (String, String, String, List<RecipeIngredientDraft>) -> Unit
) {
    var name by remember(recipe?.id) { mutableStateOf(recipe?.name.orEmpty()) }
    var notes by remember(recipe?.id) { mutableStateOf(recipe?.notes.orEmpty()) }
    var productId by remember(recipe?.id) { mutableStateOf(recipe?.productId.orEmpty()) }
    val itemsState = remember(recipe?.id) { mutableStateListOf<RecipeIngredientDraft>() }
    var error by remember(recipe?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(recipe?.id) {
        itemsState.clear()
        if (recipe != null) {
            itemsState.addAll(
                loadItems(recipe.id).map { item ->
                    RecipeIngredientDraft(
                        rawMaterialId = item.rawMaterialId,
                        mixtureId = item.mixtureId,
                        quantity = item.quantity,
                        unit = item.unit
                    )
                }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (recipe == null) "إضافة وصفة" else "تعديل الوصفة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (recipe == null) {
                    Selector(
                        label = "المنتج المرتبط",
                        value = products.firstOrNull { it.id == productId }?.name ?: "اختر المنتج",
                        options = products.map { it.id to it.name },
                        onSelect = { productId = it }
                    )
                } else {
                    Text(
                        "المنتج: " +
                            (products.firstOrNull { it.id == recipe.productId }?.name ?: recipe.productId)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الوصفة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("طريقة التحضير / ملاحظات") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("المكونات: " + itemsState.size)
                    TextButton(onClick = {
                        itemsState.add(
                            RecipeIngredientDraft(
                                quantity = 1.0,
                                unit = "G"
                            )
                        )
                    }) { Text("إضافة مكوّن") }
                }

                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(itemsState) { item ->
                        val index = itemsState.indexOf(item)
                        RecipeIngredientEditor(
                            item = item,
                            rawMaterials = rawMaterials,
                            mixtures = mixtures,
                            onChange = { itemsState[index] = it },
                            onRemove = { itemsState.removeAt(index) }
                        )
                    }
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val invalid = itemsState.any {
                    it.quantity <= 0.0 ||
                        it.unit.isBlank() ||
                        (it.rawMaterialId.isNullOrBlank() == it.mixtureId.isNullOrBlank())
                }
                when {
                    recipe == null && productId.isBlank() -> error = "اختر المنتج المرتبط"
                    name.isBlank() -> error = "أدخل اسم الوصفة"
                    itemsState.isEmpty() -> error = "أضف مكوّناً واحداً على الأقل"
                    invalid -> error = "تحقق من المكونات والكميات والوحدات"
                    recipe == null -> {
                        onCreate(
                            productId,
                            name.trim(),
                            notes.trim(),
                            itemsState.toList()
                        )
                        onDismiss()
                    }
                    else -> {
                        onUpdate(
                            recipe.id,
                            name.trim(),
                            notes.trim(),
                            itemsState.toList()
                        )
                        onDismiss()
                    }
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun MixtureEditorDialog(
    mixture: MixtureEntity?,
    rawMaterials: List<RawMaterialEntity>,
    loadItems: suspend (String) -> List<MixtureItemEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, String, List<MixtureIngredientDraft>) -> Unit,
    onUpdate: (String, String, String, Double, String, List<MixtureIngredientDraft>) -> Unit
) {
    var name by remember(mixture?.id) { mutableStateOf(mixture?.name.orEmpty()) }
    var unit by remember(mixture?.id) { mutableStateOf(mixture?.unit ?: "G") }
    var output by remember(mixture?.id) {
        mutableStateOf(mixture?.outputQuantity?.toString() ?: "1000")
    }
    var notes by remember(mixture?.id) { mutableStateOf(mixture?.notes.orEmpty()) }
    val itemsState = remember(mixture?.id) { mutableStateListOf<MixtureIngredientDraft>() }
    var error by remember(mixture?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(mixture?.id) {
        itemsState.clear()
        if (mixture != null) {
            itemsState.addAll(
                loadItems(mixture.id).map { item ->
                    MixtureIngredientDraft(
                        rawMaterialId = item.rawMaterialId,
                        quantity = item.quantity,
                        unit = item.unit
                    )
                }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mixture == null) "إضافة خلطة" else "تعديل الخلطة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الخلطة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = output,
                        onValueChange = { output = it },
                        label = { Text("الكمية الناتجة") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Selector(
                        label = "الوحدة",
                        value = unit,
                        options = listOf(
                            "G" to "جرام",
                            "KG" to "كجم",
                            "ML" to "مل",
                            "LITER" to "لتر"
                        ),
                        onSelect = { unit = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("المكونات: " + itemsState.size)
                    TextButton(onClick = {
                        itemsState.add(
                            MixtureIngredientDraft(
                                rawMaterialId = "",
                                quantity = 1.0,
                                unit = "G"
                            )
                        )
                    }) { Text("إضافة مكوّن") }
                }

                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(itemsState) { item ->
                        val index = itemsState.indexOf(item)
                        MixtureIngredientEditor(
                            item = item,
                            rawMaterials = rawMaterials,
                            onChange = { itemsState[index] = it },
                            onRemove = { itemsState.removeAt(index) }
                        )
                    }
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val quantity = output.toDoubleOrNull()
                when {
                    name.isBlank() -> error = "أدخل اسم الخلطة"
                    quantity == null || quantity <= 0.0 -> error = "أدخل كمية ناتج صحيحة"
                    itemsState.isEmpty() -> error = "أضف مكوّناً واحداً على الأقل"
                    itemsState.any {
                        it.rawMaterialId.isBlank() ||
                            it.quantity <= 0.0 ||
                            it.unit.isBlank()
                    } -> error = "تحقق من المكونات والكميات والوحدات"
                    mixture == null -> {
                        onCreate(
                            name.trim(),
                            unit,
                            quantity,
                            notes.trim(),
                            itemsState.toList()
                        )
                        onDismiss()
                    }
                    else -> {
                        onUpdate(
                            mixture.id,
                            name.trim(),
                            unit,
                            quantity,
                            notes.trim(),
                            itemsState.toList()
                        )
                        onDismiss()
                    }
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun Selector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(value, maxLines = 1)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onSelect(option.first)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RecipeIngredientEditor(
    item: RecipeIngredientDraft,
    rawMaterials: List<RawMaterialEntity>,
    mixtures: List<MixtureEntity>,
    onChange: (RecipeIngredientDraft) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val options = rawMaterials.map { it.id to "مادة: " + it.name } +
            mixtures.map { it.id to "خلطة: " + it.name }
        var expanded by remember(item.rawMaterialId, item.mixtureId) { mutableStateOf(false) }
        val selected = item.rawMaterialId?.let { id ->
            rawMaterials.firstOrNull { it.id == id }?.name
        } ?: item.mixtureId?.let { id ->
            mixtures.firstOrNull { it.id == id }?.name
        } ?: "اختر"

        Box(Modifier.weight(1.6f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selected, maxLines = 1, fontSize = 11.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.second) },
                        onClick = {
                            val material = rawMaterials.firstOrNull { it.id == option.first }
                            val mix = mixtures.firstOrNull { it.id == option.first }
                            onChange(
                                item.copy(
                                    rawMaterialId = material?.id,
                                    mixtureId = mix?.id,
                                    unit = material?.baseUnit ?: mix?.unit ?: item.unit
                                )
                            )
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = item.quantity.toString(),
            onValueChange = {
                onChange(item.copy(quantity = it.toDoubleOrNull() ?: 0.0))
            },
            label = { Text("الكمية") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.width(90.dp)
        )

        Selector(
            label = "الوحدة",
            value = item.unit,
            options = listOf(
                "G" to "جرام",
                "KG" to "كجم",
                "ML" to "مل",
                "LITER" to "لتر",
                "PIECE" to "حبة"
            ),
            onSelect = { onChange(item.copy(unit = it)) },
            modifier = Modifier.width(100.dp)
        )

        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "حذف المكوّن")
        }
    }
}

@Composable
private fun MixtureIngredientEditor(
    item: MixtureIngredientDraft,
    rawMaterials: List<RawMaterialEntity>,
    onChange: (MixtureIngredientDraft) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Selector(
            label = "المادة الخام",
            value = rawMaterials.firstOrNull { it.id == item.rawMaterialId }?.name
                ?: "اختر المادة",
            options = rawMaterials.map { it.id to it.name },
            onSelect = { id ->
                val material = rawMaterials.firstOrNull { it.id == id }
                onChange(
                    item.copy(
                        rawMaterialId = id,
                        unit = material?.baseUnit ?: item.unit
                    )
                )
            },
            modifier = Modifier.weight(1.6f)
        )

        OutlinedTextField(
            value = item.quantity.toString(),
            onValueChange = {
                onChange(item.copy(quantity = it.toDoubleOrNull() ?: 0.0))
            },
            label = { Text("الكمية") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.width(90.dp)
        )

        Selector(
            label = "الوحدة",
            value = item.unit,
            options = listOf(
                "G" to "جرام",
                "KG" to "كجم",
                "ML" to "مل",
                "LITER" to "لتر",
                "PIECE" to "حبة"
            ),
            onSelect = { onChange(item.copy(unit = it)) },
            modifier = Modifier.width(100.dp)
        )

        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "حذف المكوّن")
        }
    }
}
