package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import com.example.data.local.migration.MIGRATION_1_2

@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        RawMaterialEntity::class,
        ProductEntity::class,
        RecipeEntity::class,
        RecipeItemEntity::class,
        MixtureEntity::class,
        MixtureItemEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        InventoryTransactionEntity::class,
        CustomerEntity::class,
        DebtTransactionEntity::class,
        ShiftEntity::class,
        ShiftCashMovementEntity::class,
        ShiftHandoverEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        ExpenseEntity::class,
        WasteTransactionEntity::class,
        StockCountEntity::class,
        StockAdjustmentEntity::class,
        AuditLogEntity::class,
        CafeteriaSettingEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun rawMaterialDao(): RawMaterialDao
    abstract fun recipeDao(): RecipeDao
    abstract fun mixtureDao(): MixtureDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun customerDao(): CustomerDao
    abstract fun debtTransactionDao(): DebtTransactionDao
    abstract fun shiftDao(): ShiftDao
    abstract fun saleDao(): SaleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun wasteDao(): WasteDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "power_feul_pos.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Used only by the restore flow, immediately before the app is relaunched.
         * Closing Room first prevents copying a partially-flushed WAL database.
         */
        @Synchronized
        fun closeAndClearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
