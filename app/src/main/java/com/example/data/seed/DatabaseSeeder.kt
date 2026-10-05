package com.example.data.seed

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.CustomerStatus
import com.example.domain.model.UserRole
import com.example.security.PasswordHasher
import java.util.UUID

object DatabaseSeeder {
    suspend fun seedIfEmpty(db: AppDatabase) {
        val userDao = db.userDao()
        val userCount = userDao.countUsers()
        if (userCount > 0) return // Already seeded

        db.withTransaction {
            val hasher = PasswordHasher.DEFAULT

            val ownerHash = hasher.hash("1111")
            val adminHash = hasher.hash("2222")
            val supervisorHash = hasher.hash("3333")
            val ahmedHash = hasher.hash("1234")
            val saeedHash = hasher.hash("5678")
            val inventoryHash = hasher.hash("4444")

            // 1. Users
            val users = listOf(
                UserEntity(
                    name = "زياد الشامي (مالك النادي)",
                    username = "owner",
                    pinHash = ownerHash.hashHex,
                    pinSalt = ownerHash.saltHex,
                    role = UserRole.OWNER,
                    phone = "777000111"
                ),
                UserEntity(
                    name = "الكابتن صالح (المدير العام)",
                    username = "admin",
                    pinHash = adminHash.hashHex,
                    pinSalt = adminHash.saltHex,
                    role = UserRole.ADMIN,
                    phone = "777000222"
                ),
                UserEntity(
                    name = "محمد اليافعي (المشرف)",
                    username = "supervisor",
                    pinHash = supervisorHash.hashHex,
                    pinSalt = supervisorHash.saltHex,
                    role = UserRole.SUPERVISOR,
                    phone = "777000333"
                ),
                UserEntity(
                    name = "أحمد مسعود (كاشير)",
                    username = "ahmed",
                    pinHash = ahmedHash.hashHex,
                    pinSalt = ahmedHash.saltHex,
                    role = UserRole.CASHIER,
                    phone = "777000444"
                ),
                UserEntity(
                    name = "سعيد باوزير (كاشير)",
                    username = "saeed",
                    pinHash = saeedHash.hashHex,
                    pinSalt = saeedHash.saltHex,
                    role = UserRole.CASHIER,
                    phone = "777000555"
                ),
                UserEntity(
                    name = "عمر باحكيم (مسؤول المخزون)",
                    username = "inventory",
                    pinHash = inventoryHash.hashHex,
                    pinSalt = inventoryHash.saltHex,
                    role = UserRole.INVENTORY_MANAGER,
                    phone = "777000666"
                )
            )
            for (u in users) userDao.insertUser(u)

        // 2. Categories
        val categoryDao = db.categoryDao()
        val catJuices = CategoryEntity(name = "العصائر والسموذي", iconName = "local_cafe", displayOrder = 1)
        val catProtein = CategoryEntity(name = "مشروبات الطاقة والبروتين", iconName = "fitness_center", displayOrder = 2)
        val catBowls = CategoryEntity(name = "سلطات ووجبات خفيفة", iconName = "restaurant", displayOrder = 3)
        val catCoffee = CategoryEntity(name = "قهوة ومشروبات دافئة", iconName = "coffee", displayOrder = 4)
        val catSupplements = CategoryEntity(name = "إضافات ومكملات رياضية", iconName = "bolt", displayOrder = 5)

        categoryDao.insertCategory(catJuices)
        categoryDao.insertCategory(catProtein)
        categoryDao.insertCategory(catBowls)
        categoryDao.insertCategory(catCoffee)
        categoryDao.insertCategory(catSupplements)

        // 3. Raw Materials
        val rawDao = db.rawMaterialDao()
        val banana = RawMaterialEntity(name = "موز بلدي", sku = "RM-BAN-01", baseUnit = "KG", currentStock = 25.0, minStock = 5.0, lastPurchasePrice = 600.0, avgCostPerUnit = 600.0)
        val milk = RawMaterialEntity(name = "حليب طازج", sku = "RM-MLK-01", baseUnit = "LITER", currentStock = 30.0, minStock = 8.0, lastPurchasePrice = 800.0, avgCostPerUnit = 800.0)
        val dates = RawMaterialEntity(name = "تمر صقعي فاخر", sku = "RM-DAT-01", baseUnit = "KG", currentStock = 15.0, minStock = 3.0, lastPurchasePrice = 2000.0, avgCostPerUnit = 2000.0)
        val oats = RawMaterialEntity(name = "شوفان حبة كاملة", sku = "RM-OAT-01", baseUnit = "KG", currentStock = 20.0, minStock = 4.0, lastPurchasePrice = 1200.0, avgCostPerUnit = 1200.0)
        val honey = RawMaterialEntity(name = "عسل سدر طبيعي", sku = "RM-HNY-01", baseUnit = "KG", currentStock = 10.0, minStock = 2.0, lastPurchasePrice = 8000.0, avgCostPerUnit = 8000.0)
        val whey = RawMaterialEntity(name = "واي بروتين شوكولاتة", sku = "RM-WHY-01", baseUnit = "KG", currentStock = 8.0, minStock = 2.0, lastPurchasePrice = 18000.0, avgCostPerUnit = 18000.0)
        val lemon = RawMaterialEntity(name = "ليمون بلدي", sku = "RM-LMN-01", baseUnit = "KG", currentStock = 12.0, minStock = 3.0, lastPurchasePrice = 700.0, avgCostPerUnit = 700.0)
        val ginger = RawMaterialEntity(name = "زنجبيل طازج", sku = "RM-GNG-01", baseUnit = "KG", currentStock = 5.0, minStock = 1.0, lastPurchasePrice = 1500.0, avgCostPerUnit = 1500.0)
        val apple = RawMaterialEntity(name = "تفاح أخضر", sku = "RM-APL-01", baseUnit = "KG", currentStock = 15.0, minStock = 4.0, lastPurchasePrice = 1800.0, avgCostPerUnit = 1800.0)
        val coffeeBeans = RawMaterialEntity(name = "بن يمني فاخر", sku = "RM-COF-01", baseUnit = "KG", currentStock = 6.0, minStock = 1.5, lastPurchasePrice = 4500.0, avgCostPerUnit = 4500.0)
        val almonds = RawMaterialEntity(name = "لوز محمص", sku = "RM-ALM-01", baseUnit = "KG", currentStock = 10.0, minStock = 2.0, lastPurchasePrice = 5000.0, avgCostPerUnit = 5000.0)
        val cashews = RawMaterialEntity(name = "كاجو ممتاز", sku = "RM-CSH-01", baseUnit = "KG", currentStock = 8.0, minStock = 2.0, lastPurchasePrice = 6500.0, avgCostPerUnit = 6500.0)
        val peanuts = RawMaterialEntity(name = "فول سوداني", sku = "RM-PNT-01", baseUnit = "KG", currentStock = 15.0, minStock = 3.0, lastPurchasePrice = 2000.0, avgCostPerUnit = 2000.0)

        val materials = listOf(banana, milk, dates, oats, honey, whey, lemon, ginger, apple, coffeeBeans, almonds, cashews, peanuts)
        for (m in materials) rawDao.insertRawMaterial(m)

        // 4. Mixtures (خلطة مكسرات Power)
        val mixtureDao = db.mixtureDao()
        val mixedNuts = MixtureEntity(
            name = "خلطة مكسرات Power",
            unit = "G",
            outputQuantity = 1000.0,
            totalCost = 4500.0,
            unitCost = 4.5,
            notes = "خلطة مكسرات مطحونة معززة للطاقة"
        )
        mixtureDao.insertMixture(mixedNuts)
        val mixtureItems = listOf(
            MixtureItemEntity(mixtureId = mixedNuts.id, rawMaterialId = almonds.id, name = almonds.name, quantity = 400.0, unit = "G", costContribution = 2000.0),
            MixtureItemEntity(mixtureId = mixedNuts.id, rawMaterialId = cashews.id, name = cashews.name, quantity = 300.0, unit = "G", costContribution = 1950.0),
            MixtureItemEntity(mixtureId = mixedNuts.id, rawMaterialId = peanuts.id, name = peanuts.name, quantity = 300.0, unit = "G", costContribution = 600.0)
        )
        mixtureDao.insertMixtureItems(mixtureItems)

        // 5. Products & Recipes
        val productDao = db.productDao()
        val recipeDao = db.recipeDao()

        // Product 1: Power Full
        val prodPowerFull = ProductEntity(name = "Power Full", categoryId = catJuices.id, price = 1000.0, costPrice = 424.0, sku = "PF-01")
        productDao.insertProduct(prodPowerFull)
        val recipePowerFull = RecipeEntity(productId = prodPowerFull.id, name = "Power Full Recipe v1", version = 1, calculatedCost = 424.0)
        recipeDao.insertRecipe(recipePowerFull)
        val itemsPowerFull = listOf(
            RecipeItemEntity(recipeId = recipePowerFull.id, rawMaterialId = banana.id, name = banana.name, quantity = 100.0, unit = "G", costContribution = 60.0),
            RecipeItemEntity(recipeId = recipePowerFull.id, rawMaterialId = milk.id, name = milk.name, quantity = 130.0, unit = "ML", costContribution = 104.0),
            RecipeItemEntity(recipeId = recipePowerFull.id, rawMaterialId = dates.id, name = dates.name, quantity = 50.0, unit = "G", costContribution = 100.0),
            RecipeItemEntity(recipeId = recipePowerFull.id, mixtureId = mixedNuts.id, name = mixedNuts.name, quantity = 20.0, unit = "G", costContribution = 90.0),
            RecipeItemEntity(recipeId = recipePowerFull.id, rawMaterialId = oats.id, name = oats.name, quantity = 30.0, unit = "G", costContribution = 36.0),
            RecipeItemEntity(recipeId = recipePowerFull.id, rawMaterialId = honey.id, name = honey.name, quantity = 20.0, unit = "G", costContribution = 160.0)
        )
        recipeDao.insertRecipeItems(itemsPowerFull)

        // Product 2: Power Full Max
        val prodPowerFullMax = ProductEntity(name = "Power Full Max", categoryId = catProtein.id, price = 1500.0, costPrice = 650.0, sku = "PF-02")
        productDao.insertProduct(prodPowerFullMax)

        // Product 3: Power Full VIP
        val prodPowerFullVip = ProductEntity(name = "Power Full VIP", categoryId = catProtein.id, price = 2000.0, costPrice = 850.0, sku = "PF-03")
        productDao.insertProduct(prodPowerFullVip)

        // Product 4: Green Detox
        val prodGreenDetox = ProductEntity(name = "Green Detox", categoryId = catJuices.id, price = 1000.0, costPrice = 320.0, sku = "PF-04")
        productDao.insertProduct(prodGreenDetox)

        // Product 5: Lemon Ginger
        val prodLemonGinger = ProductEntity(name = "Lemon Ginger", categoryId = catJuices.id, price = 800.0, costPrice = 240.0, sku = "PF-05")
        productDao.insertProduct(prodLemonGinger)

        // Product 6: Whey Protein Shake
        val prodWheyShake = ProductEntity(name = "Whey Protein Shake", categoryId = catProtein.id, price = 1500.0, costPrice = 680.0, sku = "PF-06")
        productDao.insertProduct(prodWheyShake)

        // Product 7: Fruit Protein Bowl
        val prodFruitBowl = ProductEntity(name = "سلطة فواكه بروتين بول", categoryId = catBowls.id, price = 1200.0, costPrice = 510.0, sku = "PF-07")
        productDao.insertProduct(prodFruitBowl)

        // Product 8: Double Espresso
        val prodEspresso = ProductEntity(name = "إسبريسو رياضي مضاعف", categoryId = catCoffee.id, price = 600.0, costPrice = 120.0, sku = "PF-08")
        productDao.insertProduct(prodEspresso)

        // 6. Suppliers
        val supplierDao = db.supplierDao()
        supplierDao.insertSupplier(SupplierEntity(name = "شركة الألبان الطازجة", phone = "01-234567", address = "صنعاء - شارع تعز"))
        supplierDao.insertSupplier(SupplierEntity(name = "مؤسسة فواكه البركة", phone = "01-345678", address = "صنعاء - سوق الجملة"))
        supplierDao.insertSupplier(SupplierEntity(name = "وكالة التغذية الرياضية Power Nutrition", phone = "771234567", address = "عدن - المعلا"))

        // 7. Customers with Debts
        val customerDao = db.customerDao()
        val customers = listOf(
            CustomerEntity(name = "الكابتن عبدالله القحطاني", phone = "771112233", creditLimit = 25000.0, currentDebt = 4000.0, allowDebt = true),
            CustomerEntity(name = "المهندس طارق الذبحاني", phone = "772223344", creditLimit = 15000.0, currentDebt = 2500.0, allowDebt = true),
            CustomerEntity(name = "الكابتن هشام باحشوان", phone = "773334455", creditLimit = 20000.0, currentDebt = 0.0, allowDebt = true),
            CustomerEntity(name = "الدكتور فؤاد المحمودي", phone = "774445566", creditLimit = 30000.0, currentDebt = 8000.0, allowDebt = true),
            CustomerEntity(name = "اللاعب خالد العمودي", phone = "775556677", creditLimit = 10000.0, currentDebt = 9500.0, allowDebt = true, status = CustomerStatus.CREDIT_LIMIT_REACHED)
        )
        for (c in customers) customerDao.insertCustomer(c)

        // 8. Settings
        val settingsDao = db.settingsDao()
        settingsDao.setSetting(CafeteriaSettingEntity("cafeteria_name", "POWER FEUL"))
        settingsDao.setSetting(CafeteriaSettingEntity("gym_name", "Power Home Gym"))
        settingsDao.setSetting(CafeteriaSettingEntity("currency", "YER"))
        settingsDao.setSetting(CafeteriaSettingEntity("print_auto", "false"))
        }
    }
}
