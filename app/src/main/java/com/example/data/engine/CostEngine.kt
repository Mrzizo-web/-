package com.example.data.engine

import com.example.data.local.dao.MixtureDao
import com.example.data.local.dao.RawMaterialDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.entity.RecipeItemEntity

object UnitConverter {
    /**
     * Converts a given quantity from unit [fromUnit] to [toUnit].
     * Base units:
     * Weight: KG is base (1 KG = 1000 G)
     * Volume: LITER is base (1 LITER = 1000 ML)
     * Discrete: PIECE, BOTTLE, BOX
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        val from = fromUnit.trim().uppercase()
        val to = toUnit.trim().uppercase()
        if (from == to) return quantity

        // Weight conversions
        if (from == "G" && to == "KG") return quantity / 1000.0
        if (from == "KG" && to == "G") return quantity * 1000.0

        // Volume conversions
        if (from == "ML" && (to == "L" || to == "LITER")) return quantity / 1000.0
        if ((from == "L" || from == "LITER") && to == "ML") return quantity * 1000.0

        // Default: return unchanged if same category or unknown
        return quantity
    }
}

class CostEngine(
    private val rawMaterialDao: RawMaterialDao,
    private val mixtureDao: MixtureDao,
    private val recipeDao: RecipeDao
) {
    /**
     * Calculates the unit cost for a single recipe ingredient item.
     */
    suspend fun calculateItemCost(item: RecipeItemEntity): Double {
        if (!item.rawMaterialId.isNullOrEmpty()) {
            val material = rawMaterialDao.getRawMaterialById(item.rawMaterialId) ?: return 0.0
            // Material price is per base unit (e.g. per 1 KG or 1 L or 1 Piece)
            val convertedQty = UnitConverter.convert(item.quantity, item.unit, material.baseUnit)
            return convertedQty * material.lastPurchasePrice
        } else if (!item.mixtureId.isNullOrEmpty()) {
            val mixture = mixtureDao.getMixtureById(item.mixtureId) ?: return 0.0
            val convertedQty = UnitConverter.convert(item.quantity, item.unit, mixture.unit)
            return convertedQty * mixture.unitCost
        }
        return 0.0
    }

    /**
     * Calculates total cost of a recipe by summing costs of its ingredient items.
     */
    suspend fun calculateRecipeTotalCost(recipeId: String): Double {
        val items = recipeDao.getRecipeItemsSync(recipeId)
        var total = 0.0
        for (item in items) {
            total += calculateItemCost(item)
        }
        return total
    }

    /**
     * Recomputes mixture cost based on current constituent raw materials.
     */
    suspend fun calculateMixtureUnitCost(mixtureId: String): Pair<Double, Double> {
        val mixture = mixtureDao.getMixtureById(mixtureId) ?: return Pair(0.0, 0.0)
        val items = mixtureDao.getMixtureItemsSync(mixtureId)
        var totalCost = 0.0
        for (item in items) {
            val material = rawMaterialDao.getRawMaterialById(item.rawMaterialId)
            if (material != null) {
                val convertedQty = UnitConverter.convert(item.quantity, item.unit, material.baseUnit)
                totalCost += convertedQty * material.lastPurchasePrice
            }
        }
        val unitCost = if (mixture.outputQuantity > 0) totalCost / mixture.outputQuantity else 0.0
        return Pair(totalCost, unitCost)
    }
}
