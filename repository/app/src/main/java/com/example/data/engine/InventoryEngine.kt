package com.example.data.engine

import com.example.data.local.dao.InventoryTransactionDao
import com.example.data.local.dao.MixtureDao
import com.example.data.local.dao.RawMaterialDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.entity.InventoryTransactionEntity
import com.example.domain.model.InventoryTxType

class InventoryEngine(
    private val rawMaterialDao: RawMaterialDao,
    private val recipeDao: RecipeDao,
    private val mixtureDao: MixtureDao,
    private val inventoryTransactionDao: InventoryTransactionDao
) {
    /**
     * Consumes ingredients for a given product sale quantity.
     * Expands any nested mixtures to their respective raw materials.
     */
    suspend fun consumeForProduct(
        productId: String,
        quantity: Int,
        saleId: String,
        userId: String,
        userName: String
    ) {
        val recipe = recipeDao.getActiveRecipeForProductSync(productId) ?: return
        val items = recipeDao.getRecipeItemsSync(recipe.id)

        for (item in items) {
            val totalNeeded = item.quantity * quantity
            if (!item.rawMaterialId.isNullOrEmpty()) {
                deductRawMaterial(
                    materialId = item.rawMaterialId,
                    amount = totalNeeded,
                    unit = item.unit,
                    referenceId = saleId,
                    userId = userId,
                    userName = userName,
                    txType = InventoryTxType.SALE_CONSUMPTION,
                    note = "استهلاك بيع فاتورة #$saleId"
                )
            } else if (!item.mixtureId.isNullOrEmpty()) {
                // Expand mixture ingredients
                val mixtureItems = mixtureDao.getMixtureItemsSync(item.mixtureId)
                val mixture = mixtureDao.getMixtureById(item.mixtureId)
                val mixtureRatio = if (mixture != null && mixture.outputQuantity > 0) {
                    totalNeeded / mixture.outputQuantity
                } else 1.0

                for (mItem in mixtureItems) {
                    deductRawMaterial(
                        materialId = mItem.rawMaterialId,
                        amount = mItem.quantity * mixtureRatio,
                        unit = mItem.unit,
                        referenceId = saleId,
                        userId = userId,
                        userName = userName,
                        txType = InventoryTxType.SALE_CONSUMPTION,
                        note = "استهلاك خلطة (${item.name}) فاتورة #$saleId"
                    )
                }
            }
        }
    }

    /**
     * Restores raw materials when a sale is voided/returned.
     */
    suspend fun restoreForProduct(
        productId: String,
        quantity: Int,
        saleId: String,
        userId: String,
        userName: String
    ) {
        val recipe = recipeDao.getActiveRecipeForProductSync(productId) ?: return
        val items = recipeDao.getRecipeItemsSync(recipe.id)

        for (item in items) {
            val totalNeeded = item.quantity * quantity
            if (!item.rawMaterialId.isNullOrEmpty()) {
                addRawMaterialStock(
                    materialId = item.rawMaterialId,
                    amount = totalNeeded,
                    unit = item.unit,
                    referenceId = saleId,
                    userId = userId,
                    userName = userName,
                    txType = InventoryTxType.RETURN,
                    note = "إرجاع مخزون بسبب إلغاء فاتورة #$saleId"
                )
            } else if (!item.mixtureId.isNullOrEmpty()) {
                val mixtureItems = mixtureDao.getMixtureItemsSync(item.mixtureId)
                val mixture = mixtureDao.getMixtureById(item.mixtureId)
                val mixtureRatio = if (mixture != null && mixture.outputQuantity > 0) {
                    totalNeeded / mixture.outputQuantity
                } else 1.0

                for (mItem in mixtureItems) {
                    addRawMaterialStock(
                        materialId = mItem.rawMaterialId,
                        amount = mItem.quantity * mixtureRatio,
                        unit = mItem.unit,
                        referenceId = saleId,
                        userId = userId,
                        userName = userName,
                        txType = InventoryTxType.RETURN,
                        note = "إرجاع مخزون خلطة (${item.name}) بسبب إلغاء فاتورة #$saleId"
                    )
                }
            }
        }
    }

    suspend fun deductRawMaterial(
        materialId: String,
        amount: Double,
        unit: String,
        referenceId: String,
        userId: String,
        userName: String,
        txType: InventoryTxType,
        note: String
    ) {
        val material = rawMaterialDao.getRawMaterialById(materialId) ?: return
        val convertedAmount = UnitConverter.convert(amount, unit, material.baseUnit)
        val prevStock = material.currentStock
        val newStock = (prevStock - convertedAmount).coerceAtLeast(0.0)

        rawMaterialDao.updateStock(materialId, newStock)

        val tx = InventoryTransactionEntity(
            rawMaterialId = materialId,
            rawMaterialName = material.name,
            type = txType,
            quantityChange = -convertedAmount,
            unit = material.baseUnit,
            previousQuantity = prevStock,
            newQuantity = newStock,
            referenceId = referenceId,
            notes = note,
            userId = userId,
            userName = userName
        )
        inventoryTransactionDao.insertTransaction(tx)
    }

    suspend fun addRawMaterialStock(
        materialId: String,
        amount: Double,
        unit: String,
        referenceId: String,
        userId: String,
        userName: String,
        txType: InventoryTxType,
        note: String
    ) {
        val material = rawMaterialDao.getRawMaterialById(materialId) ?: return
        val convertedAmount = UnitConverter.convert(amount, unit, material.baseUnit)
        val prevStock = material.currentStock
        val newStock = prevStock + convertedAmount

        rawMaterialDao.updateStock(materialId, newStock)

        val tx = InventoryTransactionEntity(
            rawMaterialId = materialId,
            rawMaterialName = material.name,
            type = txType,
            quantityChange = convertedAmount,
            unit = material.baseUnit,
            previousQuantity = prevStock,
            newQuantity = newStock,
            referenceId = referenceId,
            notes = note,
            userId = userId,
            userName = userName
        )
        inventoryTransactionDao.insertTransaction(tx)
    }
}
