package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.UserRole
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String,
    val pinHash: String,
    val pinSalt: String,
    val role: UserRole,
    val phone: String = "",
    val isActive: Boolean = true,
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val lastLoginAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconName: String = "local_cafe",
    val displayOrder: Int = 0,
    val isActive: Boolean = true
)

@Entity(tableName = "raw_materials")
data class RawMaterialEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sku: String = "",
    val baseUnit: String, // G, KG, ML, LITER, PIECE, BOTTLE, BOX
    val currentStock: Double,
    val minStock: Double,
    val lastPurchasePrice: Double, // Price per base unit (e.g. per 1 KG or 1 L or 1 Piece)
    val avgCostPerUnit: Double,
    val supplierId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val categoryId: String,
    val price: Double, // Selling price in YER
    val costPrice: Double = 0.0, // Computed cost in YER
    val isActive: Boolean = true,
    val isAvailable: Boolean = true,
    val sku: String = "",
    val barcode: String = "",
    val imageUri: String? = null,
    val recipeId: String? = null,
    val minStockAlert: Double = 5.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val name: String,
    val version: Int = 1,
    val notes: String = "",
    val calculatedCost: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recipe_items")
data class RecipeItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val recipeId: String,
    val rawMaterialId: String? = null,
    val mixtureId: String? = null,
    val name: String,
    val quantity: Double,
    val unit: String, // G, KG, ML, LITER, PIECE
    val costContribution: Double = 0.0
)

@Entity(tableName = "mixtures")
data class MixtureEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val unit: String, // G, KG, ML, LITER
    val outputQuantity: Double,
    val totalCost: Double,
    val unitCost: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mixture_items")
data class MixtureItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val mixtureId: String,
    val rawMaterialId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val costContribution: Double
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val isActive: Boolean = true
)
