package com.example.domain.model

data class UserProfile(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val phone: String = "",
    val isActive: Boolean = true,
    val isLocked: Boolean = false,
    val lockRemainingSeconds: Long = 0L,
    val lastLoginAt: Long? = null
)
