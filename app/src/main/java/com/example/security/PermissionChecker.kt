package com.example.security

import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole

object PermissionChecker {

    private val rolePermissions: Map<UserRole, Set<AppPermission>> = mapOf(
        UserRole.OWNER to AppPermission.values().toSet(),
        UserRole.ADMIN to setOf(
            AppPermission.ACCESS_ADMIN,
            AppPermission.MANAGE_PRODUCTS,
            AppPermission.MANAGE_RECIPES,
            AppPermission.VIEW_COSTS,
            AppPermission.MANAGE_INVENTORY,
            AppPermission.ADJUST_STOCK,
            AppPermission.MANAGE_CUSTOMERS,
            AppPermission.MANAGE_DEBTS,
            AppPermission.OVERRIDE_CREDIT,
            AppPermission.MANAGE_EMPLOYEES,
            AppPermission.VIEW_REPORTS,
            AppPermission.VIEW_AUDIT_LOGS,
            AppPermission.VOID_SALE,
            AppPermission.MANAGE_SETTINGS
        ),
        UserRole.SUPERVISOR to setOf(
            AppPermission.ACCESS_ADMIN,
            AppPermission.VIEW_REPORTS,
            AppPermission.VOID_SALE,
            AppPermission.MANAGE_RECIPES,
            AppPermission.MANAGE_CUSTOMERS,
            AppPermission.MANAGE_DEBTS
        ),
        UserRole.INVENTORY_MANAGER to setOf(
            AppPermission.ACCESS_ADMIN,
            AppPermission.MANAGE_INVENTORY,
            AppPermission.ADJUST_STOCK,
            AppPermission.MANAGE_RECIPES
        ),
        UserRole.CASHIER to emptySet()
    )

    fun hasPermission(role: UserRole?, permission: AppPermission): Boolean {
        if (role == null) return false
        return rolePermissions[role]?.contains(permission) == true
    }

    fun hasPermission(user: UserEntity?, permission: AppPermission): Boolean {
        if (user == null || !user.isActive) return false
        return hasPermission(user.role, permission)
    }

    fun getPermissionsForRole(role: UserRole): Set<AppPermission> {
        return rolePermissions[role] ?: emptySet()
    }
}
