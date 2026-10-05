package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.CustomerStatus
import com.example.domain.model.InventoryTxType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import com.example.domain.model.WasteReason

class Converters {
    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = runCatching { UserRole.valueOf(value) }.getOrDefault(UserRole.CASHIER)

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = runCatching { PaymentMethod.valueOf(value) }.getOrDefault(PaymentMethod.CASH)

    @TypeConverter
    fun fromShiftStatus(value: ShiftStatus): String = value.name

    @TypeConverter
    fun toShiftStatus(value: String): ShiftStatus = runCatching { ShiftStatus.valueOf(value) }.getOrDefault(ShiftStatus.OPEN)

    @TypeConverter
    fun fromCustomerStatus(value: CustomerStatus): String = value.name

    @TypeConverter
    fun toCustomerStatus(value: String): CustomerStatus = runCatching { CustomerStatus.valueOf(value) }.getOrDefault(CustomerStatus.ACTIVE)

    @TypeConverter
    fun fromInventoryTxType(value: InventoryTxType): String = value.name

    @TypeConverter
    fun toInventoryTxType(value: String): InventoryTxType = runCatching { InventoryTxType.valueOf(value) }.getOrDefault(InventoryTxType.SALE_CONSUMPTION)

    @TypeConverter
    fun fromWasteReason(value: WasteReason): String = value.name

    @TypeConverter
    fun toWasteReason(value: String): WasteReason = runCatching { WasteReason.valueOf(value) }.getOrDefault(WasteReason.SPOILAGE)
}
