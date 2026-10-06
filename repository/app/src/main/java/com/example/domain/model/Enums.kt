package com.example.domain.model

import com.example.security.AppPermission
import com.example.security.PermissionChecker

enum class UserRole(val titleAr: String) {
    OWNER("المالك"),
    ADMIN("المدير"),
    SUPERVISOR("المشرف"),
    CASHIER("كاشير / عامل"),
    INVENTORY_MANAGER("مسؤول المخزون");

    val canAccessAdmin: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.ACCESS_ADMIN)

    val canViewCosts: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.VIEW_COSTS)

    val canViewRecipes: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.MANAGE_RECIPES)

    val canManageInventory: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.MANAGE_INVENTORY)

    val canManageEmployees: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.MANAGE_EMPLOYEES)

    val canViewReports: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.VIEW_REPORTS)

    val canOverrideCreditLimit: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.OVERRIDE_CREDIT)

    val canVoidSales: Boolean
        get() = PermissionChecker.hasPermission(this, AppPermission.VOID_SALE)
}

enum class PaymentMethod(val titleAr: String) {
    CASH("نقدي"),
    E_WALLET("محفظة إلكترونية"),
    DEBT("دين")
}

enum class ShiftStatus(val titleAr: String) {
    OPEN("مفتوح"),
    CLOSED("مغلق"),
    HANDED_OVER("تم التسليم"),
    DISCREPANCY("يوجد فرق")
}

enum class CustomerStatus(val titleAr: String) {
    ACTIVE("نشط"),
    BLOCKED("محظور"),
    CREDIT_LIMIT_REACHED("تجاوز الحد الائتماني")
}

enum class InventoryTxType(val titleAr: String) {
    PURCHASE("شراء توريد"),
    SALE_CONSUMPTION("استهلاك مبيعات"),
    WASTE("هدر وتالف"),
    ADJUSTMENT("تسوية جرد"),
    RETURN("إرجاع"),
    MANUAL_RECEIPT("إدخال يدوي")
}

enum class WasteReason(val titleAr: String) {
    SPOILAGE("تلف مواد"),
    PREP_ERROR("خطأ في التحضير"),
    ACCIDENTAL_SPILL("سقوط أو كسر"),
    EXPIRED("انتهاء صلاحية"),
    OTHER("سبب آخر")
}

enum class UnitType(val symbolAr: String, val baseUnitMultiplier: Double) {
    G("جرام", 1.0),
    KG("كجم", 1000.0),
    ML("مل", 1.0),
    LITER("لتر", 1000.0),
    PIECE("حبة", 1.0),
    BOTTLE("قارورة", 1.0),
    BOX("علبة / كرتون", 1.0);

    companion object {
        fun convert(amount: Double, from: UnitType, to: UnitType): Double {
            if (from == to) return amount
            // Gram to Kilogram
            if (from == G && to == KG) return amount / 1000.0
            if (from == KG && to == G) return amount * 1000.0
            // ML to Liter
            if (from == ML && to == LITER) return amount / 1000.0
            if (from == LITER && to == ML) return amount * 1000.0
            return amount
        }
    }
}
