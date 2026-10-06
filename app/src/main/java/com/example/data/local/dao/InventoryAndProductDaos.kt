package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'OWNER' AND isActive = 1")
    suspend fun countActiveOwners(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role IN ('OWNER', 'ADMIN') AND isActive = 1")
    suspend fun countActiveAdminsAndOwners(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET isActive = 0, updatedAt = :now WHERE id = :id")
    suspend fun deactivateUser(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE users SET isActive = :isActive, updatedAt = :now WHERE id = :id")
    suspend fun setUserActiveStatus(id: String, isActive: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE users SET failedAttempts = :attempts, lockedUntil = :lockedUntil, updatedAt = :now WHERE id = :id")
    suspend fun updateFailedAttempts(id: String, attempts: Int, lockedUntil: Long?, now: Long = System.currentTimeMillis())

    @Query("UPDATE users SET failedAttempts = 0, lockedUntil = NULL, lastLoginAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun recordSuccessfulLogin(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE users SET pinHash = :pinHash, pinSalt = :pinSalt, failedAttempts = 0, lockedUntil = NULL, updatedAt = :now WHERE id = :id")
    suspend fun updatePin(id: String, pinHash: String, pinSalt: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE users SET failedAttempts = 0, lockedUntil = NULL, updatedAt = :now WHERE id = :id")
    suspend fun unlockUser(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY displayOrder ASC, name ASC")
    fun getActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY displayOrder ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND categoryId = :categoryId ORDER BY name ASC")
    fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET isAvailable = :isAvailable WHERE id = :id")
    suspend fun updateAvailability(id: String, isAvailable: Boolean)

    @Query("UPDATE products SET isActive = 0 WHERE id = :id")
    suspend fun softDeleteProduct(id: String)
}

@Dao
interface RawMaterialDao {
    @Query("SELECT * FROM raw_materials ORDER BY name ASC")
    fun getAllRawMaterials(): Flow<List<RawMaterialEntity>>

    @Query("SELECT * FROM raw_materials ORDER BY name ASC")
    suspend fun getAllRawMaterialsSync(): List<RawMaterialEntity>

    @Query("SELECT * FROM raw_materials WHERE currentStock <= minStock ORDER BY currentStock ASC")
    suspend fun getLowStockMaterialsSync(): List<RawMaterialEntity>

    @Query("SELECT * FROM raw_materials WHERE currentStock <= minStock ORDER BY currentStock ASC")
    fun getLowStockMaterials(): Flow<List<RawMaterialEntity>>

    @Query("SELECT * FROM raw_materials WHERE id = :id LIMIT 1")
    suspend fun getRawMaterialById(id: String): RawMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterial(material: RawMaterialEntity)

    @Update
    suspend fun updateRawMaterial(material: RawMaterialEntity)

    @Query("UPDATE raw_materials SET currentStock = :newStock, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStock(id: String, newStock: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM raw_materials WHERE id = :id")
    suspend fun deleteRawMaterial(id: String)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE productId = :productId AND isActive = 1 ORDER BY version DESC LIMIT 1")
    fun getActiveRecipeForProduct(productId: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE productId = :productId AND isActive = 1 ORDER BY version DESC LIMIT 1")
    suspend fun getActiveRecipeForProductSync(productId: String): RecipeEntity?

    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes ORDER BY name ASC")
    suspend fun getAllRecipesSync(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun getRecipeById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId")
    fun getRecipeItems(recipeId: String): Flow<List<RecipeItemEntity>>

    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun getRecipeItemsSync(recipeId: String): List<RecipeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipeItems(items: List<RecipeItemEntity>)

    @Query("DELETE FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun deleteRecipeItems(recipeId: String)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("UPDATE recipes SET isActive = :isActive WHERE id = :id")
    suspend fun setRecipeActive(id: String, isActive: Boolean)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: String)

    @Query("SELECT COUNT(*) FROM recipe_items WHERE mixtureId = :mixtureId")
    suspend fun countRecipeItemsUsingMixture(mixtureId: String): Int
}

@Dao
interface MixtureDao {
    @Query("SELECT * FROM mixtures ORDER BY name ASC")
    fun getAllMixtures(): Flow<List<MixtureEntity>>

    @Query("SELECT * FROM mixtures ORDER BY name ASC")
    suspend fun getAllMixturesSync(): List<MixtureEntity>

    @Query("SELECT * FROM mixtures WHERE id = :id LIMIT 1")
    suspend fun getMixtureById(id: String): MixtureEntity?

    @Query("SELECT * FROM mixture_items WHERE mixtureId = :mixtureId")
    fun getMixtureItems(mixtureId: String): Flow<List<MixtureItemEntity>>

    @Query("SELECT * FROM mixture_items WHERE mixtureId = :mixtureId")
    suspend fun getMixtureItemsSync(mixtureId: String): List<MixtureItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixture(mixture: MixtureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixtureItems(items: List<MixtureItemEntity>)

    @Query("DELETE FROM mixture_items WHERE mixtureId = :mixtureId")
    suspend fun deleteMixtureItems(mixtureId: String)

    @Update
    suspend fun updateMixture(mixture: MixtureEntity)

    @Query("DELETE FROM mixtures WHERE id = :id")
    suspend fun deleteMixture(id: String)
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity)

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)
}
