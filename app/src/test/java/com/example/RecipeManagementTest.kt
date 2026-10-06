package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.MixtureIngredientDraft
import com.example.data.engine.RecipeIngredientDraft
import com.example.data.engine.RecipeManagementEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.RawMaterialEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole
import com.example.security.PasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeManagementTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: RecipeManagementEngine
    private lateinit var owner: UserEntity
    private val hasher = PasswordHasher.DEFAULT

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = RecipeManagementEngine(db)

        val hash = hasher.hash("2468")
        owner = UserEntity(
            id = "owner-recipe",
            name = "مالك الاختبار",
            username = "owner_recipe",
            pinHash = hash.hashHex,
            pinSalt = hash.saltHex,
            role = UserRole.OWNER
        )
        db.userDao().insertUser(owner)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createAndUpdateRecipeRecalculatesCostAndVersion() = runBlocking {
        db.categoryDao().insertCategory(
            CategoryEntity(id = "cat", name = "مشروبات")
        )
        db.rawMaterialDao().insertRawMaterial(
            RawMaterialEntity(
                id = "milk",
                name = "حليب",
                baseUnit = "KG",
                currentStock = 5.0,
                minStock = 0.5,
                lastPurchasePrice = 1000.0,
                avgCostPerUnit = 1000.0
            )
        )
        db.productDao().insertProduct(
            ProductEntity(
                id = "shake",
                name = "شيك",
                categoryId = "cat",
                price = 2500.0
            )
        )

        val created = engine.createRecipe(
            owner,
            "shake",
            "وصفة الشيك",
            "اخلط جيداً",
            listOf(
                RecipeIngredientDraft(
                    rawMaterialId = "milk",
                    quantity = 500.0,
                    unit = "G"
                )
            )
        )

        assertTrue(created.isSuccess)
        val recipe = created.getOrThrow()
        assertEquals(1, recipe.version)
        assertEquals(500.0, recipe.calculatedCost, 0.001)

        val product = db.productDao().getProductById("shake")!!
        assertEquals(recipe.id, product.recipeId)
        assertEquals(500.0, product.costPrice, 0.001)

        val updated = engine.updateRecipe(
            owner,
            recipe.id,
            "وصفة الشيك المعدلة",
            "تحديث",
            listOf(
                RecipeIngredientDraft(
                    rawMaterialId = "milk",
                    quantity = 1000.0,
                    unit = "G"
                )
            )
        ).getOrThrow()

        assertEquals(2, updated.version)
        assertEquals(1000.0, updated.calculatedCost, 0.001)
        assertEquals(1000.0, db.productDao().getProductById("shake")!!.costPrice, 0.001)
        assertEquals(1, db.recipeDao().getRecipeItemsSync(recipe.id).size)
    }

    @Test
    fun mixtureCanBeCreatedAndCannotBeDeletedWhileReferenced() = runBlocking {
        db.rawMaterialDao().insertRawMaterial(
            RawMaterialEntity(
                id = "oats",
                name = "شوفان",
                baseUnit = "KG",
                currentStock = 5.0,
                minStock = 0.5,
                lastPurchasePrice = 2000.0,
                avgCostPerUnit = 2000.0
            )
        )
        db.categoryDao().insertCategory(CategoryEntity(id = "cat2", name = "خلطة"))
        db.productDao().insertProduct(
            ProductEntity(
                id = "bar",
                name = "بار",
                categoryId = "cat2",
                price = 3000.0
            )
        )

        val mixture = engine.createMixture(
            owner,
            "خلطة الشوفان",
            "G",
            1000.0,
            "تحضير دفعة",
            listOf(
                MixtureIngredientDraft(
                    rawMaterialId = "oats",
                    quantity = 500.0,
                    unit = "G"
                )
            )
        ).getOrThrow()

        assertEquals(1000.0, mixture.totalCost, 0.001)
        assertEquals(1.0, mixture.unitCost, 0.001)

        val recipe = engine.createRecipe(
            owner,
            "bar",
            "وصفة البار",
            "",
            listOf(
                RecipeIngredientDraft(
                    mixtureId = mixture.id,
                    quantity = 200.0,
                    unit = "G"
                )
            )
        ).getOrThrow()

        assertEquals(200.0, recipe.calculatedCost, 0.001)

        val deleteResult = engine.deleteMixture(owner, mixture.id)
        assertFalse(deleteResult.isSuccess)
    }

    @Test
    fun cashierCannotManageRecipes() = runBlocking {
        val hash = hasher.hash("1357")
        val cashier = owner.copy(
            id = "cashier-recipe",
            username = "cashier_recipe",
            role = UserRole.CASHIER,
            pinHash = hash.hashHex,
            pinSalt = hash.saltHex
        )

        db.categoryDao().insertCategory(CategoryEntity(id = "cat3", name = "مشروبات"))
        db.productDao().insertProduct(
            ProductEntity(
                id = "drink",
                name = "مشروب",
                categoryId = "cat3",
                price = 1000.0
            )
        )

        val result = engine.createRecipe(
            cashier,
            "drink",
            "وصفة ممنوعة",
            "",
            listOf(
                RecipeIngredientDraft(
                    rawMaterialId = "missing",
                    quantity = 1.0,
                    unit = "G"
                )
            )
        )

        assertTrue(result.isFailure)
        assertEquals("غير مصرح لك بإدارة الوصفات والخلطات", result.exceptionOrNull()?.message)
    }
}
