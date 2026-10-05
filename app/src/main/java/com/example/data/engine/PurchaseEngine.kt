package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.InventoryTxType
import java.util.UUID

data class NewPurchaseItem(
    val rawMaterialId: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double
)

class PurchaseEngine(
    private val db: AppDatabase,
    private val inventoryEngine: InventoryEngine,
    private val costEngine: CostEngine
) {
    private val purchaseDao = db.purchaseDao()
    private val rawMaterialDao = db.rawMaterialDao()
    private val recipeDao = db.recipeDao()
    private val mixtureDao = db.mixtureDao()
    private val productDao = db.productDao()
    private val auditLogDao = db.auditLogDao()

    suspend fun createPurchase(
        user: UserEntity,
        supplierId: String,
        supplierName: String,
        invoiceNumber: String,
        items: List<NewPurchaseItem>,
        notes: String = ""
    ): Result<PurchaseEntity> {
        if (items.isEmpty()) {
            return Result.failure(Exception("قائمة المشتريات فارغة"))
        }

        val totalAmount = items.sumOf { it.quantity * it.unitPrice }
        val purchaseId = UUID.randomUUID().toString()

        val purchase = PurchaseEntity(
            id = purchaseId,
            supplierId = supplierId,
            supplierName = supplierName,
            invoiceNumber = invoiceNumber,
            totalAmount = totalAmount,
            notes = notes,
            createdByUserId = user.id,
            createdByUserName = user.name
        )

        return try {
            db.withTransaction {
                purchaseDao.insertPurchase(purchase)

                val purchaseItems = mutableListOf<PurchaseItemEntity>()

                for (item in items) {
                    val mat = rawMaterialDao.getRawMaterialById(item.rawMaterialId)
                    val matName = mat?.name ?: "مادة خام"
                    val totalPrice = item.quantity * item.unitPrice

                    purchaseItems.add(
                        PurchaseItemEntity(
                            purchaseId = purchaseId,
                            rawMaterialId = item.rawMaterialId,
                            rawMaterialName = matName,
                            quantity = item.quantity,
                            unit = item.unit,
                            unitPrice = item.unitPrice,
                            totalPrice = totalPrice
                        )
                    )

                    // 1. Increase stock via InventoryEngine
                    inventoryEngine.addRawMaterialStock(
                        materialId = item.rawMaterialId,
                        amount = item.quantity,
                        unit = item.unit,
                        referenceId = invoiceNumber,
                        userId = user.id,
                        userName = user.name,
                        txType = InventoryTxType.PURCHASE,
                        note = "توريد مشتريات فاتورة #$invoiceNumber من $supplierName"
                    )

                    // 2. Update material unit price
                    if (mat != null) {
                        val baseQty = UnitConverter.convert(item.quantity, item.unit, mat.baseUnit)
                        val pricePerBaseUnit = if (baseQty > 0) totalPrice / baseQty else item.unitPrice
                        rawMaterialDao.updateRawMaterial(
                            mat.copy(
                                lastPurchasePrice = pricePerBaseUnit,
                                supplierId = supplierId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                purchaseDao.insertPurchaseItems(purchaseItems)

                // 3. Recalculate Recipe & Product Costs for all active products
                recomputeAllRecipeCosts()

                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = "PURCHASE",
                        entityType = "PURCHASE",
                        entityId = invoiceNumber,
                        previousValue = "",
                        newValue = "$totalAmount YER",
                        notes = "شراء مواد خام بفاتورة $invoiceNumber من $supplierName بإجمالي $totalAmount ريال"
                    )
                )
            }
            Result.success(purchase)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recomputeAllRecipeCosts() {
        // 1. Recompute Mixtures
        val allMixtures = mixtureDao.getAllMixturesSync()
        for (mix in allMixtures) {
            val (totalCost, unitCost) = costEngine.calculateMixtureUnitCost(mix.id)
            mixtureDao.updateMixture(mix.copy(totalCost = totalCost, unitCost = unitCost))
        }

        // 2. Recompute Recipes
        val allRecipes = recipeDao.getAllRecipesSync()
        for (recipe in allRecipes) {
            val newTotalCost = costEngine.calculateRecipeTotalCost(recipe.id)
            recipeDao.updateRecipe(recipe.copy(calculatedCost = newTotalCost))

            // 3. Update the associated Product cost price
            val product = productDao.getProductById(recipe.productId)
            if (product != null) {
                productDao.updateProduct(product.copy(costPrice = newTotalCost))
            }
        }
    }
}
