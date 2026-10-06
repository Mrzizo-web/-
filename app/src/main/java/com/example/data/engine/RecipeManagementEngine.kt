package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.security.AppPermission
import com.example.security.PermissionChecker

data class RecipeIngredientDraft(
    val rawMaterialId: String? = null,
    val mixtureId: String? = null,
    val quantity: Double,
    val unit: String
)

data class MixtureIngredientDraft(
    val rawMaterialId: String,
    val quantity: Double,
    val unit: String
)

class RecipeManagementEngine(private val db: AppDatabase) {
    private val recipeDao = db.recipeDao()
    private val mixtureDao = db.mixtureDao()
    private val productDao = db.productDao()
    private val rawMaterialDao = db.rawMaterialDao()
    private val costEngine = CostEngine(rawMaterialDao, mixtureDao, recipeDao)
    private val auditDao = db.auditLogDao()

    suspend fun createRecipe(user: UserEntity, productId: String, name: String, notes: String, items: List<RecipeIngredientDraft>): Result<RecipeEntity> =
        runCatching {
            requirePermission(user)
            require(name.isNotBlank()) { "اسم الوصفة مطلوب" }
            require(items.isNotEmpty()) { "الوصفة يجب أن تحتوي على مكوّن واحد على الأقل" }

            db.withTransaction {
                val product = productDao.getProductById(productId) ?: error("المنتج المرتبط غير موجود")
                require(recipeDao.getActiveRecipeForProductSync(productId) == null) {
                    "هذا المنتج لديه وصفة نشطة بالفعل. استخدم التعديل على الوصفة الحالية."
                }
                validateRecipeItems(items)

                val recipe = RecipeEntity(
                    productId = productId,
                    name = name.trim(),
                    version = 1,
                    notes = notes.trim(),
                    calculatedCost = 0.0,
                    isActive = true
                )
                recipeDao.insertRecipe(recipe)

                val entities = items.map { draft ->
                    val normalizedUnit = draft.unit.trim().uppercase()
                    val itemName = ingredientDisplayName(draft)
                    val base = RecipeItemEntity(
                        recipeId = recipe.id,
                        rawMaterialId = draft.rawMaterialId,
                        mixtureId = draft.mixtureId,
                        name = itemName,
                        quantity = draft.quantity,
                        unit = normalizedUnit,
                        costContribution = 0.0
                    )
                    base.copy(costContribution = costEngine.calculateItemCost(base))
                }
                recipeDao.insertRecipeItems(entities)

                val totalCost = costEngine.calculateRecipeTotalCost(recipe.id)
                val updatedRecipe = recipe.copy(calculatedCost = totalCost)
                recipeDao.updateRecipe(updatedRecipe)
                productDao.updateProduct(product.copy(recipeId = recipe.id, costPrice = totalCost))
                audit(user, "RECIPE_CREATED", recipe.id, "تم إنشاء الوصفة " + recipe.name + " للمنتج " + product.name)
                updatedRecipe
            }
        }

    suspend fun updateRecipe(user: UserEntity, recipeId: String, name: String, notes: String, items: List<RecipeIngredientDraft>): Result<RecipeEntity> =
        runCatching {
            requirePermission(user)
            require(name.isNotBlank()) { "اسم الوصفة مطلوب" }
            require(items.isNotEmpty()) { "الوصفة يجب أن تحتوي على مكوّن واحد على الأقل" }

            db.withTransaction {
                val existing = recipeDao.getRecipeById(recipeId) ?: error("الوصفة غير موجودة")
                validateRecipeItems(items)
                recipeDao.deleteRecipeItems(recipeId)

                val draftRecipe = existing.copy(
                    name = name.trim(),
                    notes = notes.trim(),
                    version = existing.version + 1
                )
                recipeDao.updateRecipe(draftRecipe)

                val entities = items.map { draft ->
                    val normalizedUnit = draft.unit.trim().uppercase()
                    val itemName = ingredientDisplayName(draft)
                    val base = RecipeItemEntity(
                        recipeId = recipeId,
                        rawMaterialId = draft.rawMaterialId,
                        mixtureId = draft.mixtureId,
                        name = itemName,
                        quantity = draft.quantity,
                        unit = normalizedUnit,
                        costContribution = 0.0
                    )
                    base.copy(costContribution = costEngine.calculateItemCost(base))
                }
                recipeDao.insertRecipeItems(entities)

                val totalCost = costEngine.calculateRecipeTotalCost(recipeId)
                val updatedRecipe = draftRecipe.copy(calculatedCost = totalCost)
                recipeDao.updateRecipe(updatedRecipe)

                val product = productDao.getProductById(existing.productId)
                if (product != null) {
                    productDao.updateProduct(product.copy(recipeId = recipeId, costPrice = totalCost))
                }
                audit(user, "RECIPE_UPDATED", recipeId, "تم تحديث الوصفة " + updatedRecipe.name + " إلى الإصدار v" + updatedRecipe.version)
                updatedRecipe
            }
        }

    suspend fun setRecipeActive(user: UserEntity, recipeId: String, isActive: Boolean): Result<Unit> =
        runCatching {
            requirePermission(user)
            db.withTransaction {
                val recipe = recipeDao.getRecipeById(recipeId) ?: error("الوصفة غير موجودة")
                val product = productDao.getProductById(recipe.productId)
                if (!isActive && product?.isAvailable == true) {
                    error("لا يمكن تعطيل وصفة لمنتج متاح للبيع. عطّل توفر المنتج أولاً لمنع البيع دون خصم المكونات.")
                }
                if (isActive) {
                    val other = recipeDao.getActiveRecipeForProductSync(recipe.productId)
                    require(other == null || other.id == recipeId) { "يوجد بالفعل إصدار نشط آخر لهذا المنتج" }
                }
                recipeDao.setRecipeActive(recipeId, isActive)
                audit(
                    user,
                    if (isActive) "RECIPE_ENABLED" else "RECIPE_DISABLED",
                    recipeId,
                    if (isActive) "تم تفعيل الوصفة " + recipe.name else "تم تعطيل الوصفة " + recipe.name
                )
            }
        }

    suspend fun deleteRecipe(user: UserEntity, recipeId: String): Result<Unit> =
        runCatching {
            requirePermission(user)
            db.withTransaction {
                val recipe = recipeDao.getRecipeById(recipeId) ?: error("الوصفة غير موجودة")
                val product = productDao.getProductById(recipe.productId)
                require(product?.isAvailable != true) {
                    "لا يمكن حذف وصفة لمنتج متاح للبيع. عطّل توفر المنتج أولاً."
                }
                recipeDao.deleteRecipeItems(recipeId)
                recipeDao.deleteRecipe(recipeId)
                if (product != null && product.recipeId == recipeId) {
                    productDao.updateProduct(product.copy(recipeId = null))
                }
                audit(user, "RECIPE_DELETED", recipeId, "تم حذف الوصفة " + recipe.name)
            }
        }

    suspend fun createMixture(user: UserEntity, name: String, unit: String, outputQuantity: Double, notes: String, items: List<MixtureIngredientDraft>): Result<MixtureEntity> =
        runCatching {
            requirePermission(user)
            require(name.isNotBlank()) { "اسم الخلطة مطلوب" }
            require(outputQuantity > 0.0) { "كمية الناتج يجب أن تكون أكبر من صفر" }
            require(items.isNotEmpty()) { "الخلطة يجب أن تحتوي على مكوّن واحد على الأقل" }
            validateMixtureItems(items)

            db.withTransaction {
                val mixture = MixtureEntity(
                    name = name.trim(),
                    unit = unit.trim().uppercase(),
                    outputQuantity = outputQuantity,
                    totalCost = 0.0,
                    unitCost = 0.0,
                    notes = notes.trim()
                )
                mixtureDao.insertMixture(mixture)
                insertMixtureItemsAndRecalculate(mixture, items)
                val updated = mixtureDao.getMixtureById(mixture.id) ?: error("فشل حفظ الخلطة")
                audit(user, "MIXTURE_CREATED", mixture.id, "تم إنشاء الخلطة " + mixture.name)
                updated
            }
        }

    suspend fun updateMixture(user: UserEntity, mixtureId: String, name: String, unit: String, outputQuantity: Double, notes: String, items: List<MixtureIngredientDraft>): Result<MixtureEntity> =
        runCatching {
            requirePermission(user)
            require(name.isNotBlank()) { "اسم الخلطة مطلوب" }
            require(outputQuantity > 0.0) { "كمية الناتج يجب أن تكون أكبر من صفر" }
            require(items.isNotEmpty()) { "الخلطة يجب أن تحتوي على مكوّن واحد على الأقل" }
            validateMixtureItems(items)

            db.withTransaction {
                val existing = mixtureDao.getMixtureById(mixtureId) ?: error("الخلطة غير موجودة")
                mixtureDao.deleteMixtureItems(mixtureId)
                val updatedBase = existing.copy(
                    name = name.trim(),
                    unit = unit.trim().uppercase(),
                    outputQuantity = outputQuantity,
                    notes = notes.trim()
                )
                mixtureDao.updateMixture(updatedBase)
                insertMixtureItemsAndRecalculate(updatedBase, items)
                val updated = mixtureDao.getMixtureById(mixtureId) ?: error("فشل تحديث الخلطة")
                audit(user, "MIXTURE_UPDATED", mixtureId, "تم تحديث الخلطة " + updated.name)
                updated
            }
        }

    suspend fun deleteMixture(user: UserEntity, mixtureId: String): Result<Unit> =
        runCatching {
            requirePermission(user)
            db.withTransaction {
                val mixture = mixtureDao.getMixtureById(mixtureId) ?: error("الخلطة غير موجودة")
                require(recipeDao.countRecipeItemsUsingMixture(mixtureId) == 0) {
                    "لا يمكن حذف الخلطة لأنها مستخدمة داخل وصفة. عدّل الوصفة أولاً ثم أعد المحاولة."
                }
                mixtureDao.deleteMixtureItems(mixtureId)
                mixtureDao.deleteMixture(mixtureId)
                audit(user, "MIXTURE_DELETED", mixtureId, "تم حذف الخلطة " + mixture.name)
            }
        }

    private suspend fun insertMixtureItemsAndRecalculate(mixture: MixtureEntity, items: List<MixtureIngredientDraft>) {
        val entities = items.map { draft ->
            val material = rawMaterialDao.getRawMaterialById(draft.rawMaterialId)
                ?: error("المادة الخام غير موجودة: " + draft.rawMaterialId)
            val normalizedUnit = draft.unit.trim().uppercase()
            val cost = UnitConverter.convert(draft.quantity, normalizedUnit, material.baseUnit) * material.lastPurchasePrice
            MixtureItemEntity(
                mixtureId = mixture.id,
                rawMaterialId = material.id,
                name = material.name,
                quantity = draft.quantity,
                unit = normalizedUnit,
                costContribution = cost
            )
        }
        mixtureDao.insertMixtureItems(entities)
        val costs = costEngine.calculateMixtureUnitCost(mixture.id)
        mixtureDao.updateMixture(mixture.copy(totalCost = costs.first, unitCost = costs.second))
    }

    private suspend fun validateRecipeItems(items: List<RecipeIngredientDraft>) {
        items.forEach { item ->
            require(item.quantity > 0.0) { "كل كمية في الوصفة يجب أن تكون أكبر من صفر" }
            require(item.unit.isNotBlank()) { "وحدة المكوّن مطلوبة" }
            val hasRaw = !item.rawMaterialId.isNullOrBlank()
            val hasMixture = !item.mixtureId.isNullOrBlank()
            require(hasRaw.xor(hasMixture)) { "كل مكوّن يجب أن يكون مادة خام أو خلطة" }
            if (hasRaw) {
                require(rawMaterialDao.getRawMaterialById(item.rawMaterialId!!) != null) { "المادة الخام المختارة غير موجودة" }
            }
            if (hasMixture) {
                require(mixtureDao.getMixtureById(item.mixtureId!!) != null) { "الخلطة المختارة غير موجودة" }
            }
        }
    }

    private suspend fun validateMixtureItems(items: List<MixtureIngredientDraft>) {
        items.forEach { item ->
            require(item.quantity > 0.0) { "كل كمية في الخلطة يجب أن تكون أكبر من صفر" }
            require(item.unit.isNotBlank()) { "وحدة المكوّن مطلوبة" }
            require(rawMaterialDao.getRawMaterialById(item.rawMaterialId) != null) { "المادة الخام المختارة غير موجودة" }
        }
    }

    private suspend fun ingredientDisplayName(item: RecipeIngredientDraft): String {
        item.rawMaterialId?.let { id ->
            return rawMaterialDao.getRawMaterialById(id)?.name ?: "مادة خام"
        }
        item.mixtureId?.let { id ->
            return mixtureDao.getMixtureById(id)?.name ?: "خلطة"
        }
        return "مكوّن"
    }

    private suspend fun audit(user: UserEntity, action: String, entityId: String, notes: String) {
        auditDao.insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.titleAr,
                action = action,
                entityType = "RECIPE",
                entityId = entityId,
                notes = notes
            )
        )
    }

    private fun requirePermission(user: UserEntity) {
        require(PermissionChecker.hasPermission(user, AppPermission.MANAGE_RECIPES)) {
            "غير مصرح لك بإدارة الوصفات والخلطات"
        }
    }
}