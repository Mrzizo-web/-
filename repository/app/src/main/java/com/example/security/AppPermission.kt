package com.example.security

enum class AppPermission(val titleAr: String) {
    ACCESS_ADMIN("دخول لوحة الإدارة"),
    MANAGE_PRODUCTS("إدارة المنتجات والأسعار"),
    MANAGE_RECIPES("إدارة الوصفات والخلطات"),
    VIEW_COSTS("عرض التكاليف والأرباح"),
    MANAGE_INVENTORY("إدارة المخزون والتوريد"),
    ADJUST_STOCK("تسوية المخزون والهدر"),
    MANAGE_CUSTOMERS("إدارة بيانات العملاء"),
    MANAGE_DEBTS("إدارة وسندات الديون"),
    OVERRIDE_CREDIT("تجاوز سقف الدين"),
    MANAGE_EMPLOYEES("إدارة الموظفين والصلاحيات"),
    VIEW_REPORTS("عرض التقارير والورديات"),
    VIEW_AUDIT_LOGS("عرض سجل الرقابة والتدقيق"),
    VOID_SALE("إلغاء واسترجاع الفواتير"),
    MANAGE_SETTINGS("إعدادات النظام العامة")
}
