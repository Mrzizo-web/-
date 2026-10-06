package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.ShiftStatus
import java.util.UUID

sealed class ShiftResult {
    data class Success(val shift: ShiftEntity) : ShiftResult()
    data class Error(val message: String) : ShiftResult()
}

class ShiftEngine(
    private val db: AppDatabase
) {
    private val shiftDao = db.shiftDao()
    private val auditLogDao = db.auditLogDao()

    suspend fun startShift(
        user: UserEntity,
        openingCash: Double,
        notes: String = ""
    ): ShiftResult {
        val existingOpen = shiftDao.getCurrentOpenShiftSync()
        if (existingOpen != null) {
            return ShiftResult.Error("يوجد شفت مفتوح حالياً برقم #${existingOpen.shiftNumber} بواسطة ${existingOpen.userName}. يرجى إغلاقه أولاً.")
        }

        val lastNum = (shiftDao.getLastShiftNumber() ?: 0) + 1
        val shiftId = UUID.randomUUID().toString()

        val shift = ShiftEntity(
            id = shiftId,
            shiftNumber = lastNum,
            userId = user.id,
            userName = user.name,
            status = ShiftStatus.OPEN,
            startTime = System.currentTimeMillis(),
            openingCash = openingCash,
            expectedCash = openingCash,
            notes = notes
        )

        return try {
            db.withTransaction {
                shiftDao.insertShift(shift)
                shiftDao.insertCashMovement(
                    ShiftCashMovementEntity(
                        shiftId = shiftId,
                        type = "OPENING",
                        amount = openingCash,
                        notes = "نقدية افتتاحية لبدء الشفت #$lastNum"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = "SHIFT_OPEN",
                        entityType = "SHIFT",
                        entityId = "#$lastNum",
                        previousValue = "",
                        newValue = "$openingCash YER",
                        notes = "بدء الشفت رقم $lastNum بنقدية افتتاحية $openingCash ريال"
                    )
                )
            }
            ShiftResult.Success(shift)
        } catch (e: Exception) {
            ShiftResult.Error("تعذر بدء الشفت: ${e.localizedMessage}")
        }
    }

    suspend fun closeShift(
        shiftId: String,
        user: UserEntity,
        actualCash: Double,
        handedOverCash: Double,
        leftForNextShiftCash: Double,
        notes: String = ""
    ): ShiftResult {
        val shift = shiftDao.getShiftById(shiftId) ?: return ShiftResult.Error("الشفت غير موجود")
        if (shift.status != ShiftStatus.OPEN) {
            return ShiftResult.Error("الشفت مغلق بالفعل")
        }

        val allocatedSum = handedOverCash + leftForNextShiftCash
        if (Math.abs(allocatedSum - actualCash) > 0.01) {
            return ShiftResult.Error(
                "المبلغ المسلم للمشرف (${handedOverCash.toLong()} ريال) + المبلغ المتروك للعامل التالي (${leftForNextShiftCash.toLong()} ريال) لا يساوي النقدية الفعلية الموجودة في الدرج (${actualCash.toLong()} ريال)!"
            )
        }

        val expected = shift.openingCash + shift.totalCashSales - shift.totalExpensesCash
        val discrepancy = actualCash - expected
        val finalStatus = if (Math.abs(discrepancy) > 0.01) ShiftStatus.DISCREPANCY else ShiftStatus.CLOSED

        val updatedShift = shift.copy(
            status = finalStatus,
            endTime = System.currentTimeMillis(),
            expectedCash = expected,
            actualCash = actualCash,
            handedOverCash = handedOverCash,
            leftForNextShiftCash = leftForNextShiftCash,
            discrepancyAmount = discrepancy,
            notes = notes
        )

        return try {
            db.withTransaction {
                shiftDao.updateShift(updatedShift)
                shiftDao.insertCashMovement(
                    ShiftCashMovementEntity(
                        shiftId = shiftId,
                        type = "CLOSING",
                        amount = actualCash,
                        notes = "إغلاق الشفت # ${shift.shiftNumber}. تسليم: $handedOverCash ، متبقي: $leftForNextShiftCash ، الفرق: $discrepancy"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.titleAr,
                        action = "SHIFT_CLOSE",
                        entityType = "SHIFT",
                        entityId = "#${shift.shiftNumber}",
                        previousValue = "متوقع: $expected YER",
                        newValue = "فعلي: $actualCash YER (فرق: $discrepancy YER)",
                        notes = "تم إغلاق الشفت. تسليم للمشرف: $handedOverCash ، متروك: $leftForNextShiftCash"
                    )
                )
            }
            ShiftResult.Success(updatedShift)
        } catch (e: Exception) {
            ShiftResult.Error("تعذر إغلاق الشفت: ${e.localizedMessage}")
        }
    }

    suspend fun acceptHandover(
        fromShiftId: String,
        toUser: UserEntity,
        actualReceivedCash: Double,
        notes: String = ""
    ): ShiftResult {
        val prevShift = shiftDao.getShiftById(fromShiftId)
            ?: return ShiftResult.Error("شفت التسليم السابق غير موجود")

        val expectedLeft = prevShift.leftForNextShiftCash
        val discrepancy = actualReceivedCash - expectedLeft

        // Start new shift for this user
        val startResult = startShift(toUser, actualReceivedCash, "استلام تسليم من الشفت #${prevShift.shiftNumber}")
        if (startResult is ShiftResult.Error) {
            return startResult
        }
        val newShift = (startResult as ShiftResult.Success).shift

        db.withTransaction {
            val handover = ShiftHandoverEntity(
                fromShiftId = fromShiftId,
                toShiftId = newShift.id,
                fromUserId = prevShift.userId,
                fromUserName = prevShift.userName,
                toUserId = toUser.id,
                toUserName = toUser.name,
                expectedLeftAmount = expectedLeft,
                actualReceivedAmount = actualReceivedCash,
                discrepancy = discrepancy,
                notes = notes
            )
            shiftDao.insertHandover(handover)
            shiftDao.updateShift(prevShift.copy(status = ShiftStatus.HANDED_OVER))
        }

        return ShiftResult.Success(newShift)
    }
}
